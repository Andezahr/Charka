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
        @DisplayName("«＋ Фишка»: персонаж кампании и новый NPC по шаблону встают в бой на нажатую клетку")
        void addToken() {
            Character late = member("Олежа", false);
            start();

            assertThat(field.addToken(campaign.getId(), late.getId(), null, 1, 6)).isEqualTo("Олежа");
            String bandit = field.addToken(campaign.getId(), null, "bandit", 8, 6);
            entityManager.flush();
            entityManager.clear();

            assertThat(token(late)).extracting(BattleFieldService.Token::x, BattleFieldService.Token::y).containsExactly(1, 6);
            assertThat(field.field(campaign.getId()).orElseThrow().tokens())
                    .anyMatch(t -> t.name().equals(bandit) && t.npc() && t.x() == 8 && t.y() == 6);
            assertThatThrownBy(() -> field.addToken(campaign.getId(), null, "bandit", 2, 1)).hasMessageContaining("Туда не встать");
            assertThatThrownBy(() -> field.addToken(campaign.getId(), null, "bandit", 1, 6)).hasMessageContaining("занята");
        }

        @Test
        @DisplayName("Бег за раунд — не дальше MA × 3 по пути; новый раунд обнуляет; мастер ставит без ограничения")
        void runLimit() {
            start();
            Long id = token(player).id();
            field.move(campaign.getId(), id, 0, 6, true);
            int run = field.reach(campaign.getId(), id).run();

            field.move(campaign.getId(), id, 2, 6);
            assertThat(field.reach(campaign.getId(), id).meters()).isEqualTo(run - 4);
            assertThatThrownBy(() -> field.move(campaign.getId(), id, 2 + run / 2, 6)).hasMessageContaining("Дальше бега");

            encounterService.next(campaign.getId());
            encounterService.next(campaign.getId());
            entityManager.flush();
            assertThat(field.reach(campaign.getId(), id).meters()).isEqualTo(run);
            field.move(campaign.getId(), id, 11, 0, true);
            assertThat(token(player).x()).isEqualTo(11);
        }

        @Test
        @DisplayName("После бега атака в трекере — уже второе действие, −3 отмечено (A-05)")
        void secondAction() {
            start();
            Long id = token(player).id();
            field.move(campaign.getId(), id, 0, 6, true);
            field.move(campaign.getId(), id, 1, 6);
            entityManager.flush();
            entityManager.clear();

            assertThat(encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                    .filter(row -> row.character().getId().equals(player.getId())).findFirst().orElseThrow().attackOptions())
                    .contains("action_2");
            assertThat(field.field(campaign.getId()).orElseThrow().tokens().stream()
                    .filter(token -> token.character().equals(player.getId())).findFirst().orElseThrow().actionOption()).isEqualTo("action_2");
        }

        @Test
        @DisplayName("Досягаемость: бег MA × 3, сквозь стену не идёт")
        void reach() {
            start();
            Long id = token(player).id();
            field.move(campaign.getId(), id, 4, 6, true);

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
            field.move(campaign.getId(), token(player).id(), 4, 3, true);
            field.move(campaign.getId(), token(npc).id(), 9, 3, true);

            BattleFieldService.Sight closed = field.sight(campaign.getId(), token(player).id(), token(npc).id());
            assertThat(closed.blocked()).isTrue();
            assertThat(closed.meters()).isEqualTo(10);
            // CP2020, с. 103: сквозь дверь стреляют вслепую, её SP — укрытие
            assertThat(closed.canShoot()).isTrue();
            assertThat(closed.throughLabel()).isEqualTo("Деревянная дверь SP 5");
            assertThat(closed.cover()).isEqualTo("cover_wood_door");
            assertThat(closed.blindOption()).isEqualTo("blind_fire");

            field.toggleDoor(campaign.getId(), MapDoc.left(6, 3));
            assertThat(field.sight(campaign.getId(), token(player).id(), token(npc).id()).blocked()).isFalse();
            assertThatThrownBy(() -> field.toggleDoor(campaign.getId(), MapDoc.left(6, 4))).hasMessageContaining("нет двери");
        }

        @Test
        @DisplayName("Цель за машиной: укрытие — кузов, его код идёт в диалог атаки")
        void cover() {
            start();
            field.move(campaign.getId(), token(player).id(), 0, 1, true);
            field.move(campaign.getId(), token(npc).id(), 4, 1, true);

            BattleFieldService.Sight sight = field.sight(campaign.getId(), token(player).id(), token(npc).id());

            assertThat(sight.blocked()).isFalse();
            assertThat(sight.cover()).isEqualTo("cover_car");
        }

        @Test
        @DisplayName("Сквозь кирпичную стену и вторую стену не стреляют; кто между — на линии огня")
        void wallAndInTheWay() {
            start();
            Character third = member("Олежа", false);
            encounterService.add(campaign.getId(), third.getId());
            field.move(campaign.getId(), token(player).id(), 4, 6, true);
            field.move(campaign.getId(), token(npc).id(), 8, 6, true);
            BattleFieldService.Sight brick = field.sight(campaign.getId(), token(player).id(), token(npc).id());
            assertThat(brick.blocked()).isTrue();
            assertThat(brick.throughLabel()).isEqualTo("Кирпичная стена SP 25");

            field.move(campaign.getId(), token(npc).id(), 4, 1, true);
            field.move(campaign.getId(), token(third).id(), 4, 3, true);
            BattleFieldService.Sight line = field.sight(campaign.getId(), token(player).id(), token(npc).id());
            assertThat(line.visibility()).isEqualTo("full");
            assertThat(line.inTheWay()).containsExactly("Олежа");
        }

        @Test
        @DisplayName("Поле видимости: за стеной темно, цели за ней — не видны, но прострелимы")
        void view() throws Exception {
            start();
            field.move(campaign.getId(), token(player).id(), 2, 6, true);
            field.move(campaign.getId(), token(npc).id(), 9, 6, true);

            BattleFieldService.View view = field.view(campaign.getId(), token(player).id());
            assertThat(view.cells()).hasSize(12 * 8);
            assertThat(view.cells().charAt(6 * 12 + 3)).isEqualTo('1');
            assertThat(view.cells().charAt(6 * 12 + 9)).isEqualTo('0');
            assertThat(view.targets()).singleElement().satisfies(target -> {
                assertThat(target.visibility()).isEqualTo("none");
                assertThat(target.canShoot()).isTrue();
            });

            mockMvc.perform(get("/campaign/" + campaign.getId() + "/encounter/map/participants/" + token(player).id() + "/view"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.w").value(12))
                    .andExpect(jsonPath("$.targets[0].visibility").value("none"));
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

        @Test
        @DisplayName("Мастер правит копию боя: заготовка не меняется, фишка в новой стене уходит в лоток")
        void editCopy() throws Exception {
            start();
            BattleFieldService.Token placed = token(player);
            MapDoc doc = MapDoc.parse(field.editable(campaign.getId()).doc().toJson());
            doc.stamps().add(new MapDoc.Stamp("crate", placed.x(), placed.y(), 0));
            field.updateDoc(campaign.getId(), doc.toJson());
            entityManager.flush();
            entityManager.clear();

            assertThat(token(player).x()).isNull();
            assertThat(MapDoc.parse(maps.get(campaign.getId(), map.getId()).getDoc()).stamps()).hasSize(1);
            mockMvc.perform(get("/campaign/" + campaign.getId() + "/encounter/edit-map")).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/encounter/map/doc")));

            field.saveAsMap(campaign.getId(), "Бар после драки");
            assertThat(maps.list(campaign.getId())).extracting(BattleMap::getName).contains("Бар после драки");
        }
    }
}
