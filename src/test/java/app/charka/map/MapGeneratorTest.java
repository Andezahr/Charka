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
    @ValueSource(strings = {"street", "bar", "warehouse", "parking", "corridors", "roofs", "empty"})
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
        assertThat(MapDoc.parse(doc.toJson())).isEqualTo(doc);
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
