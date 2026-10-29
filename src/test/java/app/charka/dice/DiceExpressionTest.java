package app.charka.dice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DiceExpression — разбор и запись броска")
class DiceExpressionTest {

    @Nested
    @DisplayName("Разбор")
    class ParseTests {

        @ParameterizedTest(name = "«{0}» → {1}d{2}{3}")
        @CsvSource(delimiter = '|', value = {
                "d20       | 1   | 20  | 0",
                "D20       | 1   | 20  | 0",
                "1d20      | 1   | 20  | 0",
                "2d6       | 2   | 6   | 0",
                "2d6+3     | 2   | 6   | 3",
                "3d6-1     | 3   | 6   | -1",
                "' 2 d 6 + 3 ' | 2 | 6  | 3",
                "d100      | 1   | 100 | 0",
                "10d10+0   | 10  | 10  | 0",
                "2к6+1     | 2   | 6   | 1",
                "К10       | 1   | 10  | 0",
        })
        void parses(String text, int count, int sides, int modifier) {
            assertThat(DiceExpression.parse(text)).isEqualTo(new DiceExpression(count, sides, modifier));
        }

        @ParameterizedTest(name = "«{0}» — ошибка")
        @NullAndEmptySource
        @ValueSource(strings = {"abc", "2d", "d", "6", "2d6+", "2d6++1", "2d6*2", "d6d6", "-1d6", "2d6+1-1", "1.5d6"})
        void rejectsGarbage(String text) {
            assertThatThrownBy(() -> DiceExpression.parse(text)).isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest(name = "«{0}» — вне пределов")
        @ValueSource(strings = {"0d6", "101d6", "d1", "d0", "d1001", "d6+10001", "99999999999d6"})
        void rejectsOutOfRange(String text) {
            assertThatThrownBy(() -> DiceExpression.parse(text)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest(name = "{0} записывается как {1}")
    @CsvSource({"1d20, d20", "2d6+3, 2d6+3", "3d6-1, 3d6-1", "d10+0, d10"})
    @DisplayName("Каноническая запись")
    void canonical(String text, String expected) {
        assertThat(DiceExpression.parse(text)).hasToString(expected);
    }

    @Nested
    @DisplayName("Результат")
    class ResultTests {

        @Test
        @DisplayName("сумма кубиков плюс модификатор и расшифровка")
        void totalAndDetails() {
            DiceResult result = DiceResult.of(DiceExpression.parse("2d6+3"), List.of(3, 5));

            assertThat(result.total()).isEqualTo(11);
            assertThat(result.details()).isEqualTo("[3, 5] + 3 = 11");
        }

        @Test
        @DisplayName("отрицательный модификатор")
        void negativeModifier() {
            DiceResult result = DiceResult.of(DiceExpression.parse("3d6-4"), List.of(1, 1, 1));

            assertThat(result.total()).isEqualTo(-1);
            assertThat(result.details()).isEqualTo("[1, 1, 1] - 4 = -1");
        }

        @Test
        @DisplayName("один кубик без модификатора")
        void single() {
            assertThat(DiceResult.of(DiceExpression.parse("d20"), List.of(17)).details()).isEqualTo("[17] = 17");
        }
    }
}
