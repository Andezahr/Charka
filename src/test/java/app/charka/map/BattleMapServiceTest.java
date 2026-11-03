package app.charka.map;

import app.charka.model.BattleMap;
import app.charka.model.Campaign;
import app.charka.repository.CampaignRepository;
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
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Карты кампании (M-01)")
class BattleMapServiceTest {

    @Autowired private BattleMapService service;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private GameSystemRegistry systemRegistry;
    @Autowired private EntityManager entityManager;
    @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = campaign("CP2020");
    }

    private Campaign campaign(String system) {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem(system);
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        return campaignRepository.save(created);
    }

    @Nested
    @DisplayName("Создание и правка")
    class Editing {

        @Test
        @DisplayName("Новая карта — пустая, клетка системы; копия и автосохранение")
        void createCopyUpdate() {
            BattleMap map = service.create(campaign.getId(), "  Бар ", 12, 10);
            MapDoc doc = MapDoc.parse(map.getDoc());
            assertThat(map.getName()).isEqualTo("Бар");
            assertThat(doc.w()).isEqualTo(12);
            assertThat(doc.cell()).isEqualTo(2);

            doc.walls().put(MapDoc.top(0, 0), "cover_brick");
            service.updateDoc(campaign.getId(), map.getId(), doc.toJson());
            BattleMap copy = service.copy(campaign.getId(), map.getId());
            entityManager.flush();
            entityManager.clear();

            assertThat(copy.getName()).isEqualTo("Бар (копия)");
            assertThat(MapDoc.parse(service.get(campaign.getId(), copy.getId()).getDoc()).walls()).containsKey("0,0,h");
            assertThat(service.list(campaign.getId())).extracting(BattleMap::getName).containsExactly("Бар", "Бар (копия)");
        }

        @Test
        @DisplayName("Без названия или сломанный документ — отказ")
        void refused() {
            assertThatThrownBy(() -> service.create(campaign.getId(), " ", 5, 5)).isInstanceOf(IllegalArgumentException.class);
            BattleMap map = service.create(campaign.getId(), "Улица", 5, 5);
            assertThatThrownBy(() -> service.updateDoc(campaign.getId(), map.getId(), "{\"v\":1,\"w\":0,\"h\":5,\"cell\":2}"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Чужая кампания карты не видит; удаление")
        void ownership() {
            BattleMap map = service.create(campaign.getId(), "Склад", 5, 5);
            Campaign other = campaign("CP2020");

            assertThatThrownBy(() -> service.get(other.getId(), map.getId())).isInstanceOf(NoSuchElementException.class);

            service.delete(campaign.getId(), map.getId());
            entityManager.flush();
            assertThat(service.list(campaign.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Правила системы")
    class Rules {

        @Test
        @DisplayName("CP2020: материалы — таблица укрытий, у машины мотор держит больше; бег MA × 3")
        void cp2020() {
            MapRules rules = systemRegistry.get("CP2020").getMapRules();

            assertThat(rules.material("cover_brick")).get().satisfies(brick -> {
                assertThat(brick.sp()).isEqualTo(25);
                assertThat(brick.layer()).isEqualTo(Material.Layer.WALL);
            });
            assertThat(rules.material("cover_car").orElseThrow().blocksSight()).isFalse();
            MapObject car = rules.object("car").orElseThrow();
            assertThat(car.cellAt(0, 0, 0)).isEqualTo("cover_engine");
            assertThat(rules.material(rules.defaults().get("door")).orElseThrow().layer()).isEqualTo(Material.Layer.DOOR);
            assertThat(rules.reach(Map.of("ma", new app.charka.skill.StatValue("ma", "MA", 6, 6)))).isEqualTo(18);
        }

        @Test
        @DisplayName("Универсальная: прямая, материалы без прочности, досягаемость неизвестна")
        void generic() {
            MapRules rules = systemRegistry.get("GENERIC").getMapRules();

            assertThat(rules.materials()).allMatch(material -> material.sp() == 0);
            assertThat(rules.reach(Map.of())).isNull();
            assertThat(rules.defaults().values()).allMatch(code -> rules.material(code).isPresent());
        }
    }

    @Nested
    @DisplayName("Страницы и редактор")
    class Pages {

        @Test
        @DisplayName("Список, создание открывает редактор, документ туда и обратно; сломанный — 400 текстом")
        void editor() throws Exception {
            String base = "/campaign/" + campaign.getId() + "/maps";
            String location = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base)
                            .param("name", "Склад").param("width", "10").param("height", "6"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection())
                    .andReturn().getResponse().getRedirectedUrl();
            assertThat(location).endsWith("/edit");
            String mapUrl = location.substring(0, location.length() - "/edit".length());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(location))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("data-map-editor")));
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(mapUrl + "/doc"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.doc.w").value(10))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.defaults.room").value("cover_brick"));
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(mapUrl + "/doc")
                            .contentType("application/json").content("{\"v\":1,\"w\":10,\"h\":6,\"cell\":2,\"walls\":{\"0,0,h\":\"cover_brick\"}}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(mapUrl + "/doc")
                            .contentType("application/json").content("{\"v\":1,\"w\":10,\"h\":6,\"cell\":2,\"walls\":{\"99,0,h\":\"cover_brick\"}}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Склад")));
        }

        @Test
        @DisplayName("Подложка: PNG растягивается по ширине карты, отдаётся с версией; не картинка — отказ")
        void background() throws Exception {
            BattleMap map = service.create(campaign.getId(), "План", 10, 5);
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(200, 50, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", png);
            String base = "/campaign/" + campaign.getId() + "/maps/" + map.getId();

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(base + "/background")
                            .file(new org.springframework.mock.web.MockMultipartFile("file", "plan.png", "image/png", png.toByteArray())))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
            entityManager.flush();
            entityManager.clear();

            MapDoc doc = MapDoc.parse(service.get(campaign.getId(), map.getId()).getDoc());
            assertThat(doc.bg().w()).isEqualTo(10);
            assertThat(doc.bg().h()).isEqualTo(2.5);
            String url = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base + "/doc"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(url).contains("/background?v=" + doc.bg().ver());
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base + "/background"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/png"));

            assertThatThrownBy(() -> service.setBackground(campaign.getId(), map.getId(), new byte[]{1, 2}, "text/plain"))
                    .hasMessageContaining("картинка");
            assertThatThrownBy(() -> service.setBackground(campaign.getId(), map.getId(), new byte[BattleMapService.BACKGROUND_LIMIT + 1], "image/png"))
                    .hasMessageContaining("5 МБ");
            service.removeBackground(campaign.getId(), map.getId());
            assertThat(MapDoc.parse(service.get(campaign.getId(), map.getId()).getDoc()).bg()).isNull();
        }
    }
}
