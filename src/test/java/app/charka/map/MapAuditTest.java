package app.charka.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Проверка карты на играбельность (M-21)")
class MapAuditTest {

    private static MapAudit.Report audit(String text) {
        return MapAudit.of(MapText.parse(text, MapRules.GENERIC).doc(), MapRules.GENERIC);
    }

    private static MapAudit.Check check(MapAudit.Report report, String title) {
        return report.checks().stream().filter(check -> check.title().equals(title)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Комната NPC с одной дверью — узкое место; двойная дверь — тоже одно; две двери порознь — два прохода")
    void routes() {
        String oneDoor = """
                map:
                +-+-+-+-+-+
                |. . P . .|
                +-+-+-+-+D+
                |N . . . .|
                +-+-+-+-+-+
                """;
        MapAudit.Report one = audit(oneDoor);
        assertThat(one.passages()).isEqualTo(1);
        assertThat(check(one, "Пути").mark()).isEqualTo(MapAudit.Mark.WARN);

        MapAudit.Report doubleDoor = audit(oneDoor.replace("+-+-+-+-+D+", "+-+-+-+D+D+"));
        assertThat(doubleDoor.passages()).isEqualTo(1);
        assertThat(check(doubleDoor, "Пути").mark()).isEqualTo(MapAudit.Mark.WARN);

        MapAudit.Report two = audit(oneDoor.replace("+-+-+-+-+D+", "+D+-+-+-+D+"));
        assertThat(two.passages()).isEqualTo(2);
        assertThat(check(two, "Пути").mark()).isEqualTo(MapAudit.Mark.OK);
    }

    @Test
    @DisplayName("Без зон старта или без пути — предупреждение")
    void noPath() {
        assertThat(check(audit("map:\n\n . . .\n"), "Зоны старта").mark()).isEqualTo(MapAudit.Mark.WARN);
        MapAudit.Report walled = audit("map:\n\n P|N\n");
        assertThat(walled.passages()).isZero();
        assertThat(check(walled, "Пути").detail()).contains("не дойти");
    }

    @Test
    @DisplayName("Голое поле: далеко от укрытия, прострел насквозь, плоское окружение; ящики и колонны это исправляют")
    void openField() {
        String row = " ." + " .".repeat(28) + " .";
        String field = "map:\n\n" + " P" + " .".repeat(28) + " N\n\n" + row + "\n\n" + row + "\n";

        MapAudit.Report bare = audit(field);

        assertThat(check(bare, "Укрытия").mark()).isEqualTo(MapAudit.Mark.WARN);
        assertThat(check(bare, "Прострел").mark()).isEqualTo(MapAudit.Mark.WARN);
        assertThat(bare.longestSight()).isEqualTo(30);
        assertThat(check(bare, "Окружение").mark()).isEqualTo(MapAudit.Mark.WARN);

        // ряд контейнеров с промежутками: укрытие в паре шагов от любой клетки
        String blocks = " .".repeat(3) + (" U U U" + " .".repeat(2)).repeat(5) + " .".repeat(2);
        MapAudit.Report covered = audit(field.replaceFirst("\n\n" + java.util.regex.Pattern.quote(row) + "\n", "\n\n" + blocks + "\n"));
        assertThat(covered.farFromCover()).isLessThanOrEqualTo(4);
        assertThat(covered.coverShare()).isGreaterThan(30);
        // прострел по рядам без контейнеров остался
        assertThat(check(covered, "Прострел").mark()).isEqualTo(MapAudit.Mark.WARN);
    }

    @Test
    @DisplayName("Дистанция в ходах: стороны вплотную — предупреждение")
    void distance() {
        MapAudit.Report close = audit("map:\n\n P . N\n");
        assertThat(close.pathCells()).isEqualTo(2);
        assertThat(check(close, "Дистанция").mark()).isEqualTo(MapAudit.Mark.WARN);
    }
}
