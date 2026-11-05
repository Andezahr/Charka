package app.charka.map;

import app.charka.model.Campaign;
import app.charka.repository.BattleMapRepository;
import app.charka.repository.CampaignRepository;
import app.charka.system.GameSystemRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Текстовая карта (M-20)")
class MapTextTest {

    @Autowired private GameSystemRegistry systemRegistry;
    @Autowired private MapGenerator generator;
    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private BattleMapRepository battleMapRepository;

    private static final String BAR = """
            # маленький бар
            name: Бар «Ржавчина»
            size: 6x2
            labels:
              a: Стойка
            map:
            +-+-+-+-+-+-+
            |. . . . a .|
            + +D+-+ + + +
            |P|. R R R R=
            +-+-+-+-+-+-+
            """;

    private MapRules cp2020() {
        return systemRegistry.get("CP2020").getMapRules();
    }

    @Nested
    @DisplayName("Чтение")
    class Parse {

        @Test
        @DisplayName("Стены, дверь, окно, предмет, зона и подпись — по местам; название и размер из заголовка")
        void bar() {
            MapText.Parsed parsed = MapText.parse(BAR, cp2020());
            MapDoc doc = parsed.doc();

            assertThat(parsed.name()).isEqualTo("Бар «Ржавчина»");
            assertThat(parsed.warnings()).isEmpty();
            assertThat(doc.w()).isEqualTo(6);
            assertThat(doc.h()).isEqualTo(2);
            assertThat(doc.cell()).isEqualTo(2);
            assertThat(doc.walls()).containsEntry(MapDoc.top(0, 0), "cover_brick")
                    .containsEntry(MapDoc.left(0, 0), "cover_brick")
                    .containsEntry(MapDoc.top(1, 1), "door:cover_wood_door")
                    .containsEntry(MapDoc.top(2, 1), "cover_brick")
                    .containsEntry(MapDoc.left(1, 1), "cover_brick")
                    .containsEntry(MapDoc.left(6, 1), "window:glass")
                    .containsEntry(MapDoc.top(5, 2), "cover_brick")
                    .doesNotContainKey(MapDoc.top(0, 1));
            assertThat(doc.stamps()).containsExactly(new MapDoc.Stamp("bar", 2, 1, 0));
            assertThat(doc.zones()).containsExactly(new MapDoc.Zone(MapDoc.PLAYERS, 0, 1, 1, 1));
            assertThat(doc.labels()).containsExactly(new MapDoc.Label(4, 0, "Стойка"));
        }

        @Test
        @DisplayName("Поворот предмета — по форме букв; машина на дороге стоит на асфальте; открытая дверь, бетон, проём")
        void rotationAndGround() {
            String text = """
                    size: 7x4
                    map:
                    #o#d#s#
                     , C C , , , ,

                     , C C C C C C

                     , C C C C C C

                     , C C , , , ,
                    """;

            MapDoc doc = MapText.parse(text, cp2020()).doc();

            assertThat(doc.stamps()).containsExactlyInAnyOrder(
                    new MapDoc.Stamp("car", 1, 0, 0), new MapDoc.Stamp("car", 3, 1, 1));
            assertThat(doc.cells()).containsEntry("1,0", "asphalt").containsEntry("4,2", "asphalt").containsEntry("0,0", "asphalt");
            assertThat(doc.walls()).containsEntry(MapDoc.top(0, 0), "gap")
                    .containsEntry(MapDoc.top(1, 0), "door:cover_wood_door:open")
                    .containsEntry(MapDoc.top(2, 0), "door:cover_steel_door:open");
            // углы только для глаза
            assertThat(MapText.parse("size: 1x1\nmap:\n# #\n", cp2020()).doc().walls()).isEmpty();
            assertThat(MapText.parse("size: 1x1\nmap:\n\n#.#\n", cp2020()).doc().walls()).containsEntry(MapDoc.left(0, 0), "cover_block");
        }

        @Test
        @DisplayName("Своя легенда, подпись по координатам, комментарии")
        void legend() {
            String text = """
                    name: Сквер
                    legend:
                      Q = tree      # дерево другой буквой
                      edge Z = cover_stone
                    labels:
                      1,0: Памятник
                    map:

                    ZQZ.ZQ
                    """;

            MapDoc doc = MapText.parse(text, cp2020()).doc();

            assertThat(doc.stamps()).extracting(MapDoc.Stamp::code).containsExactly("tree", "tree");
            assertThat(doc.walls()).containsEntry(MapDoc.left(0, 0), "cover_stone").containsEntry(MapDoc.left(1, 0), "cover_stone");
            assertThat(doc.labels()).containsExactly(new MapDoc.Label(1, 0, "Памятник"));
        }

        @Test
        @DisplayName("Ошибки — с номером строки и столбца: стена на месте клетки, неполный предмет, нет раздела map")
        void errors() {
            assertThatThrownBy(() -> MapText.parse("size: 2x1\nmap:\n\n.|.\n", cp2020()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Строка 4, столбец 1").hasMessageContaining("«.» на месте стены");
            assertThatThrownBy(() -> MapText.parse("map:\n\n | .\n", cp2020()))
                    .hasMessageContaining("Строка 3, столбец 2").hasMessageContaining("съехал столбец");
            assertThatThrownBy(() -> MapText.parse("map:\n\n C C\n", cp2020()))
                    .hasMessageContaining("Машина 2×4");
            assertThatThrownBy(() -> MapText.parse("name: x\n", cp2020())).hasMessageContaining("map:");
        }

        @Test
        @DisplayName("Предупреждения: предмета нет у системы, ряд короче остальных")
        void warnings() {
            MapText.Parsed generic = MapText.parse("map:\n\n B B . .\n", MapRules.GENERIC);
            assertThat(generic.warnings()).anyMatch(w -> w.contains("dumpster"));
            assertThat(generic.doc().stamps()).isEmpty();

            String uneven = """
                    map:

                     . . . .

                     . . .

                     . . . .
                    """;
            assertThat(MapText.parse(uneven, cp2020()).warnings()).anyMatch(w -> w.startsWith("Строка 5") && w.contains("пропущена"));
        }
    }

    @Nested
    @DisplayName("Запись")
    class Write {

        @ParameterizedTest
        @ValueSource(strings = {"street", "bar", "warehouse", "parking", "corridors", "roofs", "crossroads", "port", "office"})
        @DisplayName("Текст карты читается в ту же карту: стены, занятые предметами клетки, зоны, подписи; второй круг — тот же текст")
        void roundTrip(String code) {
            MapRules rules = cp2020();
            MapGenerator.Template template = MapGenerator.template(code);
            MapDoc doc = generator.generate(code, template.width(), template.height(), 5, rules);

            String text = MapText.write(template.label(), doc, rules);
            MapText.Parsed back = MapText.parse(text, rules);

            assertThat(back.warnings()).as(text).isEmpty();
            assertThat(back.name()).isEqualTo(template.label());
            assertThat(back.doc().walls()).isEqualTo(doc.walls());
            assertThat(occupied(back.doc(), rules)).isEqualTo(occupied(doc, rules));
            assertThat(back.doc().zones()).containsExactlyInAnyOrderElementsOf(doc.zones());
            assertThat(new HashSet<>(back.doc().labels())).isEqualTo(new HashSet<>(doc.labels()));
            assertThat(MapText.write(template.label(), back.doc(), rules)).isEqualTo(text);
        }

        private Set<String> occupied(MapDoc doc, MapRules rules) {
            Set<String> cells = new HashSet<>();
            doc.stamps().forEach(stamp -> {
                MapObject object = rules.object(stamp.code()).orElseThrow();
                for (int dy = 0; dy < object.height(stamp.rot()); dy++) {
                    for (int dx = 0; dx < object.width(stamp.rot()); dx++) {
                        cells.add(stamp.code() + "@" + (stamp.x() + dx) + "," + (stamp.y() + dy));
                    }
                }
            });
            return cells;
        }
    }

    @Nested
    @DisplayName("Нарисованные карты в генераторе")
    class Drawn {

        @Test
        @DisplayName("Каждый файл maps/*.map.txt читается без замечаний, от зоны игроков до NPC можно дойти")
        void files() {
            assertThat(generator.drawn()).containsKey("nightclub");
            generator.drawn().forEach((code, text) -> {
                MapText.Parsed parsed = MapText.parse(text, cp2020());
                assertThat(parsed.warnings()).as(code).isEmpty();
                assertThat(parsed.doc().zones()).extracting(MapDoc.Zone::kind).as(code).contains(MapDoc.PLAYERS, MapDoc.NPCS);
                assertThat(MapGeneratorTest.reachable(parsed.doc(), cp2020())).as(code).isTrue();
                // M-21: нарисованная карта — без единственного узкого места между сторонами
                assertThat(MapAudit.of(parsed.doc(), cp2020()).passages()).as(code).isGreaterThanOrEqualTo(2);
            });
        }

        @Test
        @DisplayName("Ночной клуб: в списке шаблонов, двери и предметы на своих местах")
        void nightclub() {
            MapGenerator.Template template = generator.templateOf("nightclub");
            assertThat(template.label()).isEqualTo("Ночной клуб «Ржавый неон»");
            assertThat(generator.templates()).last().extracting(MapGenerator.Template::code).isEqualTo("empty");

            MapDoc doc = generator.generate("nightclub", 10, 10, 1, cp2020());

            assertThat(doc.w()).isEqualTo(32);
            assertThat(doc.h()).isEqualTo(22);
            assertThat(doc.walls()).containsEntry(MapDoc.top(17, 4), "door:cover_wood_door")
                    .containsEntry(MapDoc.left(20, 4), "door:cover_steel_door")
                    .containsEntry(MapDoc.top(25, 8), "door:cover_wood_door")
                    .containsEntry(MapDoc.top(9, 11), "door:cover_wood_door:open")
                    .containsEntry(MapDoc.left(31, 12), "door:cover_steel_door")
                    .containsEntry(MapDoc.left(6, 13), "door:cover_wood_door")
                    .containsEntry(MapDoc.left(20, 13), "door:cover_wood_door")
                    .containsEntry(MapDoc.top(9, 16), "door:cover_wood_door")
                    .containsEntry(MapDoc.top(3, 16), "window:glass");
            assertThat(doc.stamps()).contains(new MapDoc.Stamp("dumpster", 28, 0, 0), new MapDoc.Stamp("bar", 2, 2, 0),
                    new MapDoc.Stamp("bar", 6, 2, 0), new MapDoc.Stamp("car", 2, 18, 1), new MapDoc.Stamp("shelf", 27, 8, 0));
            assertThat(doc.zones()).contains(new MapDoc.Zone(MapDoc.NPCS, 26, 2, 4, 2), new MapDoc.Zone(MapDoc.PLAYERS, 8, 17, 4, 1));
        }
    }

    @Nested
    @DisplayName("Загрузка и выгрузка")
    class Http {

        private Campaign campaign() {
            Campaign created = new Campaign();
            created.setName("Тест");
            created.setSystem("CP2020");
            created.setStartDate(LocalDate.of(2037, 1, 1));
            created.setCurrentDate(LocalDate.of(2037, 1, 1));
            return campaignRepository.save(created);
        }

        @Test
        @DisplayName("Вставленный текст — новая карта с названием из заголовка; файл — с именем файла; выгрузка — тот же текст")
        void importAndExport() throws Exception {
            Campaign campaign = campaign();
            String base = "/campaign/" + campaign.getId() + "/maps";

            mockMvc.perform(post(base + "/import").param("text", BAR)).andExpect(status().is3xxRedirection());
            var map = battleMapRepository.findByCampaignIdOrderByNameAsc(campaign.getId()).getFirst();
            assertThat(map.getName()).isEqualTo("Бар «Ржавчина»");

            String text = mockMvc.perform(get(base + "/" + map.getId() + "/text"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".map.txt")))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(MapText.parse(text, cp2020()).doc()).isEqualTo(MapDoc.parse(map.getDoc()));

            MockMultipartFile file = new MockMultipartFile("file", "подвал.map.txt", "text/plain",
                    BAR.replace("name: Бар «Ржавчина»\n", "").getBytes(StandardCharsets.UTF_8));
            mockMvc.perform(multipart(base + "/import").file(file)).andExpect(status().is3xxRedirection());
            assertThat(battleMapRepository.findByCampaignIdOrderByNameAsc(campaign.getId()))
                    .extracting(app.charka.model.BattleMap::getName).contains("подвал");
        }

        @Test
        @DisplayName("Ошибка в рисунке — карта не создаётся, текст возвращается в форму с ошибкой")
        void broken() throws Exception {
            Campaign campaign = campaign();

            mockMvc.perform(post("/campaign/" + campaign.getId() + "/maps/import").param("text", "map:\n\n C C\n"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("mapError", org.hamcrest.Matchers.containsString("Машина")))
                    .andExpect(flash().attribute("importText", "map:\n\n C C\n"));
            assertThat(battleMapRepository.findByCampaignIdOrderByNameAsc(campaign.getId())).isEmpty();
        }
    }
}
