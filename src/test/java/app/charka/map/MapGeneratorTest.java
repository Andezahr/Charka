package app.charka.map;

import app.charka.system.GameSystemRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Генератор карт (M-08)")
class MapGeneratorTest {

    @Autowired private MapGenerator generator;
    @Autowired private GameSystemRegistry systemRegistry;

    @ParameterizedTest
    @ValueSource(strings = {"street", "bar", "warehouse", "parking", "corridors", "roofs", "crossroads", "port", "office", "empty"})
    @DisplayName("Каждый шаблон даёт правильную карту с зонами старта, предметы не налезают, на зонах их нет")
    void valid(String code) {
        MapRules rules = systemRegistry.get("CP2020").getMapRules();
        MapGenerator.Template template = MapGenerator.template(code);
        MapDoc doc = generator.generate(code, template.width(), template.height(), 7, rules);

        doc.validate();
        assertThat(doc.zones()).extracting(MapDoc.Zone::kind).contains(MapDoc.PLAYERS, MapDoc.NPCS);
        MapGrid grid = new MapGrid(doc, rules);
        doc.zones().forEach(zone -> {
            for (int y = zone.y(); y < zone.y() + zone.h(); y++) {
                for (int x = zone.x(); x < zone.x() + zone.w(); x++) {
                    assertThat(grid.blocked(x, y)).as(code + ": зона " + x + "," + y).isFalse();
                }
            }
        });
        // материалы — из правил системы
        doc.walls().values().forEach(value -> {
            MapDoc.Edge edge = MapDoc.Edge.of(value);
            if (edge.material() != null) {
                assertThat(rules.material(edge.material())).as(value).isPresent();
            }
        });
        doc.cells().values().forEach(terrain -> assertThat(rules.material(terrain)).as(terrain).isPresent());
        assertThat(MapDoc.parse(doc.toJson())).isEqualTo(doc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"crossroads", "port", "office"})
    @DisplayName("M-19: готовая локация — свой размер, от игроков до NPC дойти можно (двери открываются), предмет не разрезан стеной")
    void location(String code) {
        MapRules rules = systemRegistry.get("CP2020").getMapRules();
        MapGenerator.Template template = MapGenerator.template(code);

        for (long seed = 1; seed <= 20; seed++) {
            MapDoc doc = generator.generate(code, 10, 10, seed, rules);

            assertThat(doc.w()).isEqualTo(template.width());
            assertThat(doc.h()).isEqualTo(template.height());
            assertThat(doc.w()).isGreaterThanOrEqualTo(60);
            assertThat(reachable(doc, rules)).as(code + " зерно " + seed).isTrue();
            for (MapDoc.Stamp stamp : doc.stamps()) {
                MapObject object = rules.object(stamp.code()).orElseThrow();
                for (int dy = 0; dy < object.height(stamp.rot()); dy++) {
                    for (int dx = 0; dx < object.width(stamp.rot()); dx++) {
                        int x = stamp.x() + dx;
                        int y = stamp.y() + dy;
                        if (dx > 0) {
                            assertThat(doc.walls()).as(code + ": " + stamp).doesNotContainKey(MapDoc.left(x, y));
                        }
                        if (dy > 0) {
                            assertThat(doc.walls()).as(code + ": " + stamp).doesNotContainKey(MapDoc.top(x, y));
                        }
                    }
                }
            }
        }
        assertThat(generator.generate(code, 10, 10, 1, systemRegistry.get("GENERIC").getMapRules()).w()).isEqualTo(template.width());
    }

    /** Из зоны игроков в зону NPC шагами по сторонам; двери проходимы — их открывают */
    private static boolean reachable(MapDoc doc, MapRules rules) {
        MapGrid grid = new MapGrid(doc, rules);
        MapDoc.Zone from = doc.zones().stream().filter(zone -> zone.kind().equals(MapDoc.PLAYERS)).findFirst().orElseThrow();
        MapDoc.Zone to = doc.zones().stream().filter(zone -> zone.kind().equals(MapDoc.NPCS)).findFirst().orElseThrow();
        boolean[][] seen = new boolean[doc.w()][doc.h()];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{from.x(), from.y()});
        seen[from.x()][from.y()] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            if (to.contains(cell[0], cell[1])) {
                return true;
            }
            for (int[] step : steps) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (!doc.inside(nx, ny) || seen[nx][ny] || grid.blocked(nx, ny)) {
                    continue;
                }
                MapDoc.Edge edge = doc.edge(MapDoc.between(cell[0], cell[1], nx, ny));
                if (edge != null && edge.kind() != MapDoc.Edge.Kind.DOOR && edge.blocksMove()) {
                    continue;
                }
                seen[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return false;
    }

    @Test
    @DisplayName("Одно зерно — одна карта, другое — другая; улица с машинами и дверями")
    void seeded() {
        MapRules rules = systemRegistry.get("CP2020").getMapRules();

        MapDoc first = generator.generate("street", 30, 18, 1, rules);
        assertThat(generator.generate("street", 30, 18, 1, rules)).isEqualTo(first);
        assertThat(generator.generate("street", 30, 18, 2, rules)).isNotEqualTo(first);
        assertThat(first.stamps()).anyMatch(stamp -> stamp.code().equals("car"));
        assertThat(first.walls().values()).anyMatch(value -> value.startsWith("door:"));
    }

    @Test
    @DisplayName("Без системных предметов генерирует то, что есть; нет шаблона — отказ")
    void generic() {
        MapRules rules = systemRegistry.get("GENERIC").getMapRules();

        MapDoc doc = generator.generate("bar", 24, 18, 3, rules);

        assertThat(doc.stamps()).allMatch(stamp -> rules.object(stamp.code()).isPresent());
        assertThatThrownBy(() -> generator.generate("castle", 10, 10, 1, rules)).isInstanceOf(IllegalArgumentException.class);
    }
}
