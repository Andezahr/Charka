package app.charka.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Геометрия карты (M-01)")
class MapGridTest {

    /** Кирпич SP 25 стеной, машина SP 10 предметом, гипсокартон SP 5 */
    private static final MapRules RULES = new MapRules() {
        @Override
        public int cellMeters() {
            return 2;
        }

        @Override
        public List<Material> materials() {
            return List.of(
                    new Material("brick", "Кирпич", 25, Material.Layer.WALL, true, true),
                    new Material("wood", "Дверь", 5, Material.Layer.DOOR, true, true),
                    new Material("car", "Кузов", 10, Material.Layer.OBJECT, true, false),
                    new Material("pillar", "Колонна", 30, Material.Layer.OBJECT, true, true));
        }

        @Override
        public List<MapObject> objects() {
            return List.of(MapObject.solid("car", "Машина", 1, 2, "car"), MapObject.solid("pillar", "Колонна", 1, 1, "pillar"));
        }

        @Override
        public Map<String, String> defaults() {
            return Map.of();
        }
    };

    private static MapDoc doc() {
        return MapDoc.empty(10, 10, 2);
    }

    @Nested
    @DisplayName("Линия взгляда")
    class Sight {

        @Test
        @DisplayName("Стена между клетками закрывает линию; открытая дверь — нет")
        void wall() {
            MapDoc doc = doc();
            doc.walls().put(MapDoc.left(3, 0), "brick");
            assertThat(new MapGrid(doc, RULES).line(0, 0, 5, 0).blocked()).isTrue();

            doc.walls().put(MapDoc.left(3, 0), "door:wood:open");
            assertThat(new MapGrid(doc, RULES).line(0, 0, 5, 0).blocked()).isFalse();
        }

        @Test
        @DisplayName("Поверх машины видно, она — укрытие цели рядом; колонна закрывает")
        void cover() {
            MapDoc doc = doc();
            doc.stamps().add(new MapDoc.Stamp("car", 4, 0, 0));
            MapGrid.Line line = new MapGrid(doc, RULES).line(0, 0, 5, 0);

            assertThat(line.blocked()).isFalse();
            assertThat(line.coverNear(1)).get().satisfies(cover -> assertThat(cover.material().code()).isEqualTo("car"));
            assertThat(new MapGrid(doc, RULES).line(0, 0, 9, 0).coverNear(1)).isEmpty();

            doc.stamps().add(new MapDoc.Stamp("pillar", 2, 0, 0));
            assertThat(new MapGrid(doc, RULES).line(0, 0, 5, 0).blocked()).isTrue();
        }

        @Test
        @DisplayName("Диагональ через угол: видно, если открыт один обход")
        void corner() {
            MapDoc doc = doc();
            doc.walls().put(MapDoc.left(1, 0), "brick");
            assertThat(new MapGrid(doc, RULES).line(0, 0, 1, 1).blocked()).isFalse();

            doc.walls().put(MapDoc.top(0, 1), "brick");
            assertThat(new MapGrid(doc, RULES).line(0, 0, 1, 1).blocked()).isTrue();
        }
    }

    @Nested
    @DisplayName("Досягаемость")
    class Reach {

        @Test
        @DisplayName("Бег 4 м: две клетки по прямой, одна по диагонали")
        void open() {
            List<int[]> cells = new MapGrid(doc(), RULES).reach(5, 5, 4);

            assertThat(cells).anyMatch(cell -> cell[0] == 7 && cell[1] == 5)
                    .anyMatch(cell -> cell[0] == 6 && cell[1] == 6)
                    .noneMatch(cell -> cell[0] == 7 && cell[1] == 6);
        }

        @Test
        @DisplayName("Сквозь стену не бежать, через предмет не пройти, угол не срезается")
        void walls() {
            MapDoc doc = doc();
            for (int y = 0; y < 10; y++) {
                doc.walls().put(MapDoc.left(6, y), "brick");
            }
            doc.stamps().add(new MapDoc.Stamp("car", 4, 4, 0));
            MapGrid grid = new MapGrid(doc, RULES);
            List<int[]> cells = grid.reach(5, 5, 20);

            assertThat(cells).noneMatch(cell -> cell[0] >= 6).noneMatch(cell -> cell[0] == 4 && cell[1] == 4);
            assertThat(grid.blocked(4, 5)).isTrue();
            assertThat(grid.blocked(-1, 0)).isTrue();
        }
    }
}
