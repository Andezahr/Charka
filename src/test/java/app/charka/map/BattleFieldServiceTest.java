package app.charka.map;

import app.charka.encounter.EncounterService;
import app.charka.model.BattleMap;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Поле боя (M-02..M-05, M-13)")
class BattleFieldServiceTest {

    @Autowired private BattleFieldService field;
    @Autowired private BattleMapService maps;
    @Autowired private EncounterService encounterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mockMvc;

    private Campaign campaign;
    private Character player;
    private Character npc;
    private BattleMap map;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        player = member("Колясик", false);
        npc = member("Бустер 1", true);
        MapDoc doc = MapDoc.empty(12, 8, 2);
        // стена с дверью между x = 5 и x = 6, машина в (2, 0)
        for (int y = 0; y < 8; y++) {
            doc.walls().put(MapDoc.left(6, y), y == 3 ? "door:cover_wood_door" : "cover_brick");
        }
        doc.stamps().add(new MapDoc.Stamp("car", 2, 0, 0));
        doc.zones().add(new MapDoc.Zone(MapDoc.NPCS, 9, 3, 2, 2));
        map = maps.save(campaign.getId(), "Бар", doc);
        entityManager.flush();
        entityManager.clear();
    }

    private Character member(String name, boolean isNpc) {
        Character character = new Character();
        character.setName(name);
        character.setNpc(isNpc);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private void start() {
        encounterService.start(campaign.getId(), List.of(player.getId(), npc.getId()), false, false, map.getId());
        entityManager.flush();
        entityManager.clear();
    }

    private BattleFieldService.Token token(Character character) {
        return field.field(campaign.getId()).orElseThrow().tokens().stream()
                .filter(token -> token.character().equals(character.getId())).findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("Начало боя")
    class Start {

        @Test
        @DisplayName("Копия карты; NPC — в свою зону, игрок без зоны — у левого края")
        void placed() {
            start();

            assertThat(token(npc).x()).isBetween(9, 10);
            assertThat(token(player).x()).isZero();
            assertThat(token(player).initials()).isEqualTo("Ко");
            assertThat(token(npc).initials()).isEqualTo("Б1");
        }

        @Test
        @DisplayName("Без карты — поля нет; карту можно положить в идущий бой")
        void attach() {
            encounterService.start(campaign.getId(), List.of(player.getId(), npc.getId()));
            assertThat(field.field(campaign.getId())).isEmpty();

            field.attach(campaign.getId(), map.getId());
            entityManager.flush();
            entityManager.clear();

            assertThat(token(player).x()).isNotNull();
        }

        @Test
        @DisplayName("Вошедший в бой встаёт на поле сам")
        void joined() {
            Character second = member("Бустер 2", true);
            start();
            encounterService.add(campaign.getId(), second.getId());
            entityManager.flush();
            entityManager.clear();

            assertThat(token(second).x()).isNotNull();
            assertThat(token(second)).extracting(t -> t.x() + "," + t.y()).isNotEqualTo(token(npc).x() + "," + token(npc).y());
        }
    }

    @Nested
    @DisplayName("Ход фишки")
    class Moves {

        @Test
        @DisplayName("На свободную клетку; в машину, на занятую и за край — отказ")
        void move() {
            start();
            Long id = token(player).id();

            field.move(campaign.getId(), id, 1, 5);
            entityManager.flush();
            entityManager.clear();
            assertThat(token(player)).extracting(BattleFieldService.Token::x, BattleFieldService.Token::y).containsExactly(1, 5);

            assertThatThrownBy(() -> field.move(campaign.getId(), id, 2, 1)).hasMessageContaining("Туда не встать");
            assertThatThrownBy(() -> field.move(campaign.getId(), id, 20, 1)).hasMessageContaining("За краем");
            BattleFieldService.Token other = token(npc);
            assertThatThrownBy(() -> field.move(campaign.getId(), id, other.x(), other.y())).hasMessageContaining("занята");
        }

        @Test
        @DisplayName("Досягаемость: бег MA × 3, сквозь стену не идёт")
        void reach() {
            start();
            Long id = token(player).id();
            field.move(campaign.getId(), id, 4, 6);

            BattleFieldService.Reach reach = field.reach(campaign.getId(), id);

            assertThat(reach.meters()).isPositive();
            assertThat(reach.cells()).isNotEmpty().noneMatch(cell -> cell[0] >= 6);
        }
    }

    @Nested
    @DisplayName("Линия взгляда и двери")
    class Sight {

        @Test
        @DisplayName("Закрытая дверь закрывает линию, открытая — нет; расстояние по прямой")
        void door() {
            start();
            field.move(campaign.getId(), token(player).id(), 4, 3);
            field.move(campaign.getId(), token(npc).id(), 9, 3);

            BattleFieldService.Sight closed = field.sight(campaign.getId(), token(player).id(), token(npc).id());
            assertThat(closed.blocked()).isTrue();
            assertThat(closed.meters()).isEqualTo(10);

            field.toggleDoor(campaign.getId(), MapDoc.left(6, 3));
            assertThat(field.sight(campaign.getId(), token(player).id(), token(npc).id()).blocked()).isFalse();
            assertThatThrownBy(() -> field.toggleDoor(campaign.getId(), MapDoc.left(6, 4))).hasMessageContaining("нет двери");
        }

        @Test
        @DisplayName("Цель за машиной: укрытие — кузов, его код идёт в диалог атаки")
        void cover() {
            start();
            field.move(campaign.getId(), token(player).id(), 0, 1);
            field.move(campaign.getId(), token(npc).id(), 4, 1);

            BattleFieldService.Sight sight = field.sight(campaign.getId(), token(player).id(), token(npc).id());

            assertThat(sight.blocked()).isFalse();
            assertThat(sight.cover()).isEqualTo("cover_car");
        }
    }

    @Nested
    @DisplayName("Опрос карты")
    class Polling {

        @Test
        @DisplayName("ETag: то же содержимое — 304, указатель меняет карту")
        void etag() throws Exception {
            start();
            String url = "/campaign/" + campaign.getId() + "/encounter/map";
            String etag = mockMvc.perform(get(url)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokens.length()").value(2))
                    .andReturn().getResponse().getHeader("ETag");

            mockMvc.perform(get(url).header("If-None-Match", etag)).andExpect(status().isNotModified());

            mockMvc.perform(post(url + "/pointer").param("x", "3").param("y", "3").param("who", "Колясик"))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get(url).header("If-None-Match", etag)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.pointers[0].who").value("Колясик"));
        }

        @Test
        @DisplayName("Ход на занятую клетку — 400 с текстом; боя без карты — 404")
        void errors() throws Exception {
            String url = "/campaign/" + campaign.getId() + "/encounter/map";
            mockMvc.perform(get(url)).andExpect(status().isNotFound());
            start();
            BattleFieldService.Token other = token(npc);

            mockMvc.perform(post(url + "/participants/" + token(player).id() + "/move")
                            .param("x", String.valueOf(other.x())).param("y", String.valueOf(other.y())))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("занята")))
                    .andExpect(header().doesNotExist("ETag"));
        }
    }
}
