package app.charka.map;

import app.charka.attack.AttackResult;
import app.charka.attack.AttackService;
import app.charka.encounter.EncounterService;
import app.charka.model.BattleMap;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.EncounterRepository;
import app.charka.service.CharacterService;
import app.charka.system.GameSystemRegistry;
import app.charka.system.cp2020.Cp2020HealthStrategy;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("M-23: окружение на карте — дым, трудная местность, выбить дверь, взрыв бочки")
class MapEnvironmentTest {

    @Autowired private BattleFieldService field;
    @Autowired private BattleMapService maps;
    @Autowired private EncounterService encounterService;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private AttackService attackService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private GameSystemRegistry systems;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character first;
    private Character second;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        first = member("Колясик");
        second = member("Олежа");
    }

    private Character member(String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private MapRules rules() {
        return systems.get("CP2020").getMapRules();
    }

    private void start(MapDoc doc) {
        BattleMap map = maps.save(campaign.getId(), "Склад", doc);
        entityManager.flush();
        entityManager.clear();
        encounterService.start(campaign.getId(), List.of(first.getId(), second.getId()), false, false, map.getId());
        entityManager.flush();
        entityManager.clear();
    }

    private Long token(Character character) {
        return field.field(campaign.getId()).orElseThrow().tokens().stream()
                .filter(token -> token.character().equals(character.getId())).findFirst().orElseThrow().id();
    }

    private MapDoc battleDoc() {
        return MapDoc.parse(encounterRepository.findByCampaignId(campaign.getId()).orElseThrow().getMapDoc());
    }

    private int damage(Character character) {
        return characterRepository.findById(character.getId()).orElseThrow().getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType())).findFirst().orElseThrow().getValue();
    }

    @Nested
    @DisplayName("Геометрия")
    class Geometry {

        @Test
        @DisplayName("Трудная местность: бег по обломкам вдвое дороже (хоумбрю)")
        void difficult() {
            MapDoc doc = MapDoc.empty(10, 1, 2);
            for (int x = 1; x < 10; x++) {
                doc.cells().put(MapDoc.cellKey(x, 0), "debris");
            }
            List<int[]> reach = new MapGrid(doc, rules()).reach(0, 0, 8);

            // 8 м по обломкам — 2 клетки (по 4 м), а не 4
            assertThat(reach).extracting(cell -> cell[0]).containsExactlyInAnyOrder(0, 1, 2);
        }

        @Test
        @DisplayName("Дым закрывает взгляд, но сквозь него стреляют вслепую; в дыму не видно")
        void smoke() {
            MapDoc doc = MapDoc.empty(10, 1, 2);
            doc.cells().put(MapDoc.cellKey(4, 0), "smoke");
            MapGrid grid = new MapGrid(doc, rules());

            MapGrid.Sight through = grid.sight(0, 0, 8, 0, 1);
            assertThat(through.visibility()).isEqualTo(MapGrid.Visibility.NONE);
            assertThat(through.smoke()).isTrue();

            MapGrid.Sight inside = grid.sight(0, 0, 4, 0, 1);
            assertThat(inside.visibility()).isEqualTo(MapGrid.Visibility.NONE);
            assertThat(inside.smoke()).isTrue();
            // вплотную видно
            assertThat(grid.sight(3, 0, 4, 0, 1).visibility()).isNotEqualTo(MapGrid.Visibility.NONE);
        }

        @Test
        @DisplayName("Поле боя: сквозь дым — «вслепую», модификатор системы")
        void smokeOnField() {
            MapDoc doc = MapDoc.empty(10, 3, 2);
            doc.cells().put(MapDoc.cellKey(4, 1), "smoke");
            start(doc);
            field.move(campaign.getId(), token(second), 8, 1, true);
            field.move(campaign.getId(), token(first), 0, 1, true);

            BattleFieldService.Sight sight = field.sight(campaign.getId(), token(first), token(second));
            assertThat(sight.blocked()).isTrue();
            assertThat(sight.canShoot()).isTrue();
            assertThat(sight.throughLabel()).isEqualTo("дым");
            assertThat(sight.blindOption()).isEqualTo("blind_fire");
        }
    }

    @Nested
    @DisplayName("Сломать")
    class Break {

        @Test
        @DisplayName("Дверь выбивает и игрок, стену — только мастер")
        void doorAndWall() {
            MapDoc doc = MapDoc.empty(10, 3, 2);
            doc.walls().put(MapDoc.left(5, 0), "door:cover_wood_door");
            doc.walls().put(MapDoc.left(5, 1), "cover_brick");
            doc.walls().put(MapDoc.left(5, 2), "window:glass");
            start(doc);

            assertThat(field.breakEdge(campaign.getId(), MapDoc.left(5, 0), false)).startsWith("Дверь выбита");
            assertThat(field.breakEdge(campaign.getId(), MapDoc.left(5, 2), false)).startsWith("Окно разбито");
            assertThatThrownBy(() -> field.breakEdge(campaign.getId(), MapDoc.left(5, 1), false)).hasMessageContaining("мастер");
            assertThat(field.breakEdge(campaign.getId(), MapDoc.left(5, 1), true)).startsWith("Стена проломлена");

            assertThat(battleDoc().walls()).containsEntry(MapDoc.left(5, 0), "gap").containsEntry(MapDoc.left(5, 1), "gap");
        }
    }

    @Nested
    @DisplayName("Взрыв")
    class Blast {

        @Test
        @DisplayName("Бочка: урон всем в радиусе 3 м, соседний баллон рвётся следом, дверь рядом вынесена, за кирпичом не задело")
        void barrelChain() {
            MapDoc doc = MapDoc.empty(12, 3, 2);
            doc.stamps().add(new MapDoc.Stamp("fuel_barrel", 3, 1, 0));
            doc.stamps().add(new MapDoc.Stamp("gas_cylinder", 4, 1, 0));
            doc.walls().put(MapDoc.left(3, 0), "door:cover_wood_door");
            // кирпич между бочкой и Олежей
            doc.walls().put(MapDoc.left(6, 1), "cover_brick");
            start(doc);
            field.move(campaign.getId(), token(first), 2, 1, true);
            field.move(campaign.getId(), token(second), 6, 1, true);
            entityManager.flush();
            when(dice.roll(anyInt())).thenReturn(6);

            AttackResult result = attackService.detonate(campaign.getId(), 3, 1, first.getId());
            entityManager.flush();
            entityManager.clear();

            assertThat(result.title()).contains("Бочка с горючим").contains("Газовый баллон");
            assertThat(result.steps()).anyMatch(step -> step.startsWith("Бочка с горючим: взрыв 4d10"))
                    .anyMatch(step -> step.contains("Взрыв снёс: дверь"));
            assertThat(damage(first)).isPositive();
            assertThat(damage(second)).isZero();
            MapDoc after = battleDoc();
            assertThat(after.stamps()).isEmpty();
            assertThat(after.cells()).containsEntry(MapDoc.cellKey(3, 1), "debris");
            assertThat(after.walls()).containsEntry(MapDoc.left(3, 0), "gap").containsEntry(MapDoc.left(6, 1), "cover_brick");
        }

        @Test
        @DisplayName("В пустой клетке взрываться нечему")
        void nothing() {
            start(MapDoc.empty(5, 5, 2));
            assertThatThrownBy(() -> attackService.detonate(campaign.getId(), 1, 1, null)).hasMessageContaining("нечему");
        }
    }
}
