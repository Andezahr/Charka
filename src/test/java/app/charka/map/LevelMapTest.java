package app.charka.map;

import app.charka.encounter.EncounterService;
import app.charka.model.BattleMap;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import app.charka.system.GameSystemRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("M-22: многоуровневые карты")
class LevelMapTest {

    /**
     * Двор 1 этаж 8×4, над ним балкон 2 этаж: пол только в левой половине (x 0..3), правая — пустота над двором.
     * Лестница в клетке (1, 1) на обоих уровнях.
     */
    static final String TEXT = """
            name: Двор с балконом
            size: 8x4
            map: 0 Двор
            +-+-+-+-+-+-+-+-+
            |. . . . . . . .|
            + + + + + + + + +
            |. ^ . . . . . .|
            + + + + + + + + +
            |. . . . . . . .|
            + + + + + + + + +
            |P P . . . . N N|
            +-+-+-+-+-+-+-+-+
            map: 1 Балкон
            +-+-+-+-+-+-+-+-+
            |. . . . # # # #|
            + + + + + + + + +
            |. ^ . . # # # #|
            + + + + + + + + +
            |. . . . # # # #|
            + + + + + + + + +
            |. . . . # # # #|
            +-+-+-+-+-+-+-+-+
            """;

    @Autowired private BattleFieldService field;
    @Autowired private BattleMapService maps;
    @Autowired private EncounterService encounterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private GameSystemRegistry systems;
    @Autowired private EntityManager entityManager;

    private MapRules rules() {
        return systems.get("CP2020").getMapRules();
    }

    @Nested
    @DisplayName("Текстовый формат")
    class Text {

        @Test
        @DisplayName("Разделы «map:» — уровни снизу вверх: названия, высота по умолчанию 3 м, лестница и пустота")
        void parse() {
            MapText.Parsed parsed = MapText.parse(TEXT, rules());
            MapDoc doc = parsed.doc();

            assertThat(doc.levelCount()).isEqualTo(2);
            assertThat(doc.levelName(0)).isEqualTo("Двор");
            assertThat(doc.levelName(1)).isEqualTo("Балкон");
            assertThat(doc.levelZ(1)).isEqualTo(3);
            assertThat(doc.cells()).containsEntry("1,1", LevelMap.STAIRS);
            assertThat(doc.level(1).cells()).containsEntry("1,1", LevelMap.STAIRS).containsEntry("5,2", LevelMap.VOID);
            assertThat(doc.zones()).hasSize(2);
        }

        @Test
        @DisplayName("Запись и чтение дают ту же карту")
        void roundTrip() {
            MapDoc doc = MapText.parse(TEXT, rules()).doc();
            String written = MapText.write("Двор с балконом", doc, rules());

            assertThat(written).contains("map: 0 Двор", "map: 1 Балкон");
            MapDoc again = MapText.parse(written, rules()).doc();
            assertThat(again.level(1).cells()).isEqualTo(doc.level(1).cells());
            assertThat(again.cells()).isEqualTo(doc.cells());
        }

        @Test
        @DisplayName("Плоская карта пишется без номеров уровней, как раньше")
        void flat() {
            MapDoc doc = MapDoc.empty(2, 2, 2);
            assertThat(MapText.write("x", doc, rules())).contains("\nmap:\n");
        }
    }

    @Nested
    @DisplayName("Геометрия")
    class Geometry {

        @Test
        @DisplayName("С балкона видно двор сквозь пустоту, укрытие внизу не мешает (с. 103); под полом балкона — не видно")
        void sightDown() {
            MapDoc doc = MapText.parse(TEXT, rules()).doc();
            doc.cells().put("6,1", "cover_curb");
            LevelMap levels = new LevelMap(doc, rules());

            var open = levels.sight(1, 2, 1, 0, 6, 1, 1).orElseThrow();
            assertThat(open.visibility()).isNotEqualTo(MapGrid.Visibility.NONE);
            assertThat(open.cover()).isEmpty();
            // клетка двора под полом балкона
            assertThat(levels.sight(1, 2, 1, 0, 2, 2, 1)).isEmpty();
            // снизу вверх — только если над смотрящим открыто
            assertThat(levels.sight(0, 6, 2, 1, 2, 1, 1)).isPresent();
            assertThat(levels.sight(0, 2, 2, 1, 2, 1, 1)).isEmpty();
            assertThat(levels.distance(1, 2, 1, 0, 6, 1)).isEqualTo(9);
        }

        @Test
        @DisplayName("Лестница ведёт на соседний уровень; подъём на 3 м — 6 м бега (хоумбрю)")
        void stairs() {
            LevelMap levels = new LevelMap(MapText.parse(TEXT, rules()).doc(), rules());
            assertThat(levels.stairsFrom(0, 1, 1)).containsExactly(1);
            assertThat(levels.stairsFrom(1, 1, 1)).containsExactly(0);
            assertThat(levels.stairsFrom(0, 2, 1)).isEmpty();
            assertThat(levels.stairsCost(0, 1)).isEqualTo(6);
            // в пустоту не встать
            assertThat(levels.grid(1).blocked(5, 1)).isTrue();
        }
    }

    @Nested
    @DisplayName("Бой")
    class Fight {

        private Campaign campaign;
        private Character player;
        private Character npc;

        @BeforeEach
        void setUp() {
            Campaign created = new Campaign();
            created.setName("Тест");
            created.setSystem("CP2020");
            created.setStartDate(LocalDate.of(2037, 1, 1));
            created.setCurrentDate(LocalDate.of(2037, 1, 1));
            campaign = campaignRepository.save(created);
            player = member("Колясик", false);
            npc = member("Бустер", true);
            BattleMap map = maps.save(campaign.getId(), "Двор", MapText.parse(TEXT, rules()).doc());
            entityManager.flush();
            entityManager.clear();
            encounterService.start(campaign.getId(), List.of(player.getId(), npc.getId()), false, false, map.getId());
            entityManager.flush();
            entityManager.clear();
        }

        private Character member(String name, boolean isNpc) {
            Character character = new Character();
            character.setName(name);
            character.setNpc(isNpc);
            return characterService.createInCampaign(campaign.getId(), character);
        }

        private BattleFieldService.Token token(Character character) {
            return field.field(campaign.getId()).orElseThrow().tokens().stream()
                    .filter(token -> token.character().equals(character.getId())).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("По лестнице на балкон: уровень сменился, бег потрачен; на другой уровень не по лестнице — отказ")
        void climb() {
            field.move(campaign.getId(), token(player).id(), 1, 1, true);

            assertThatThrownBy(() -> field.move(campaign.getId(), token(player).id(), 2, 1, false, 1))
                    .hasMessageContaining("по лестнице");
            assertThat(field.climb(campaign.getId(), token(player).id(), null, false)).isEqualTo("Балкон");
            entityManager.flush();
            entityManager.clear();

            assertThat(token(player).level()).isEqualTo(1);
            assertThat(token(player).moved()).isEqualTo(6);
            // по балкону ходят как обычно; в пустоту — нельзя
            field.move(campaign.getId(), token(player).id(), 3, 1, false);
            assertThatThrownBy(() -> field.move(campaign.getId(), token(player).id(), 4, 1, false)).hasMessageContaining("Туда не встать");
        }

        @Test
        @DisplayName("Мастер ставит без ограничения сразу на балкон; с балкона видно NPC во дворе, сверху укрытие не в счёт")
        void sightFromBalcony() {
            field.move(campaign.getId(), token(player).id(), 3, 1, true, 1);
            field.move(campaign.getId(), token(npc).id(), 6, 2, true);
            entityManager.flush();
            entityManager.clear();

            BattleFieldService.Sight sight = field.sight(campaign.getId(), token(player).id(), token(npc).id());
            assertThat(sight.blocked()).isFalse();
            assertThat(sight.cover()).isNull();
            assertThat(token(player).level()).isEqualTo(1);

            // NPC ушёл под балкон — пол закрывает
            field.move(campaign.getId(), token(npc).id(), 2, 3, true);
            assertThat(field.sight(campaign.getId(), token(player).id(), token(npc).id()).blocked()).isTrue();
        }
    }
}
