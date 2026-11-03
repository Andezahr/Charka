package app.charka.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Документ карты (M-01)")
class MapDocTest {

    @Nested
    @DisplayName("Разбор")
    class Parse {

        @Test
        @DisplayName("JSON туда и обратно; незнакомые поля пропускаются")
        void roundTrip() {
            MapDoc doc = MapDoc.empty(10, 8, 2);
            doc.walls().put(MapDoc.top(2, 3), "cover_brick");
            doc.walls().put(MapDoc.left(4, 4), "door:cover_wood_door:open");
            doc.cells().put(MapDoc.cellKey(1, 1), "debris");
            doc.stamps().add(new MapDoc.Stamp("car", 5, 2, 1));
            doc.zones().add(new MapDoc.Zone(MapDoc.PLAYERS, 0, 0, 2, 2));

            MapDoc parsed = MapDoc.parse(doc.toJson().replace("{\"v\"", "{\"future\":1,\"v\""));

            assertThat(parsed).isEqualTo(doc);
        }

        @Test
        @DisplayName("Новее приложения, за краем, неверный ключ, огромный размер — отказ")
        void refused() {
            assertThatThrownBy(() -> MapDoc.parse("{\"v\":99,\"w\":5,\"h\":5,\"cell\":2}")).hasMessageContaining("новой версией");
            assertThatThrownBy(() -> MapDoc.parse("{\"v\":1,\"w\":5,\"h\":5,\"cell\":2,\"walls\":{\"6,1,h\":\"cover_brick\"}}"))
                    .hasMessageContaining("за краем");
            assertThatThrownBy(() -> MapDoc.parse("{\"v\":1,\"w\":5,\"h\":5,\"cell\":2,\"walls\":{\"1,1,x\":\"cover_brick\"}}"))
                    .hasMessageContaining("ключ грани");
            assertThatThrownBy(() -> MapDoc.parse("{\"v\":1,\"w\":500,\"h\":5,\"cell\":2}")).hasMessageContaining("Размер");
            assertThatThrownBy(() -> MapDoc.parse("не json")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Нижний и правый край карты — грани за последней клеткой")
        void borders() {
            MapDoc doc = MapDoc.empty(5, 4, 2);
            doc.walls().put(MapDoc.top(4, 4), "cover_brick");
            doc.walls().put(MapDoc.left(5, 3), "cover_brick");

            doc.validate();
        }
    }

    @Nested
    @DisplayName("Грани")
    class Edges {

        @Test
        @DisplayName("Стена, закрытая и открытая дверь, окно, проём: кто мешает идти и смотреть")
        void kinds() {
            assertThat(MapDoc.Edge.of("cover_brick")).satisfies(edge -> {
                assertThat(edge.blocksMove()).isTrue();
                assertThat(edge.blocksSight()).isTrue();
            });
            assertThat(MapDoc.Edge.of("door:cover_wood_door").blocksSight()).isTrue();
            assertThat(MapDoc.Edge.of("door:cover_wood_door:open").blocksMove()).isFalse();
            assertThat(MapDoc.Edge.of("window:glass")).satisfies(edge -> {
                assertThat(edge.blocksMove()).isTrue();
                assertThat(edge.blocksSight()).isFalse();
            });
            assertThat(MapDoc.Edge.of("gap").blocksMove()).isFalse();
            assertThat(List.of("cover_brick", "door:cover_wood_door", "door:cover_wood_door:open", "window:glass", "gap"))
                    .allSatisfy(value -> assertThat(MapDoc.Edge.of(value).toString()).isEqualTo(value));
        }

        @Test
        @DisplayName("Грань между соседями одна с обеих сторон")
        void between() {
            assertThat(MapDoc.between(2, 3, 2, 4)).isEqualTo(MapDoc.between(2, 4, 2, 3)).isEqualTo("2,4,h");
            assertThat(MapDoc.between(3, 1, 2, 1)).isEqualTo("3,1,v");
            assertThatThrownBy(() -> MapDoc.between(0, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Предметы")
    class Objects {

        private final MapObject car = new MapObject("car", "Машина", 2, 4, List.of(
                "engine", "engine", "body", "body", "body", "body", "rear", "rear"));

        @Test
        @DisplayName("Поворот на четверть: машина ложится, мотор справа")
        void rotation() {
            assertThat(car.width(1)).isEqualTo(4);
            assertThat(car.height(1)).isEqualTo(2);
            assertThat(car.cellAt(1, 3, 0)).isEqualTo("engine");
            assertThat(car.cellAt(1, 0, 1)).isEqualTo("rear");
            assertThat(car.cellAt(2, 0, 0)).isEqualTo("rear");
            assertThat(car.cellAt(3, 0, 0)).isEqualTo("engine");
        }

        @Test
        @DisplayName("Клеток должно быть ширина × высота")
        void size() {
            assertThatThrownBy(() -> new MapObject("x", "X", 2, 2, List.of("a"))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Расстояние по прямой между центрами клеток, клетка 2 м")
    void distance() {
        assertThat(MapRules.GENERIC.distance(2, 0, 0, 3, 4)).isEqualTo(10);
        assertThat(MapRules.GENERIC.distance(2, 0, 0, 1, 1)).isEqualTo(3);
    }
}
