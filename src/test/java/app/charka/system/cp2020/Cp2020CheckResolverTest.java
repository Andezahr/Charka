package app.charka.system.cp2020;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.model.counters.strategy.health.DiceRoller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CP2020 — кубик проверки и проверка")
class Cp2020CheckResolverTest {

    /** Заранее заданные броски d10 */
    static DiceRoller dice(Integer... rolls) {
        Deque<Integer> queue = new ArrayDeque<>(Arrays.asList(rolls));
        return sides -> {
            assertThat(sides).isEqualTo(10);
            return queue.removeFirst();
        };
    }

    @Nested
    @DisplayName("Cp2020Dice.rollCheck")
    class DiceTests {

        @Test
        @DisplayName("обычный бросок — одно число")
        void plain() {
            Cp2020Dice.Roll roll = Cp2020Dice.rollCheck(dice(6));

            assertThat(roll.total()).isEqualTo(6);
            assertThat(roll.exploded()).isFalse();
            assertThat(roll.fumble()).isFalse();
            assertThat(roll.describe()).isEqualTo("6");
        }

        @Test
        @DisplayName("10 бросается ещё раз и прибавляется: «10 → +7 = 17»")
        void explodes() {
            Cp2020Dice.Roll roll = Cp2020Dice.rollCheck(dice(10, 7));

            assertThat(roll.total()).isEqualTo(17);
            assertThat(roll.exploded()).isTrue();
            assertThat(roll.describe()).isEqualTo("10 → +7 = 17");
        }

        @Test
        @DisplayName("взрыв повторяется, пока выпадает 10")
        void explodesRepeatedly() {
            Cp2020Dice.Roll roll = Cp2020Dice.rollCheck(dice(10, 10, 3));

            assertThat(roll.rolls()).containsExactly(10, 10, 3);
            assertThat(roll.describe()).isEqualTo("10 → +10 → +3 = 23");
        }

        @Test
        @DisplayName("1 на первом кубике — фамбл; 1 после взрыва — нет")
        void fumble() {
            assertThat(Cp2020Dice.rollCheck(dice(1)).fumble()).isTrue();
            assertThat(Cp2020Dice.rollCheck(dice(10, 1)).fumble()).isFalse();
        }

        @Test
        @DisplayName("бесконечные десятки ограничены")
        void capped() {
            Cp2020Dice.Roll roll = Cp2020Dice.rollCheck(sides -> 10);

            assertThat(roll.rolls()).hasSize(Cp2020Dice.MAX_EXPLOSIONS + 1);
        }
    }

    @Nested
    @DisplayName("Cp2020CheckResolver")
    class ResolverTests {

        private final Cp2020CheckResolver resolver = new Cp2020CheckResolver();

        @ParameterizedTest(name = "бросок {0} + {1} vs {2} → {3}")
        @CsvSource({"5, 10, 15, true", "4, 10, 15, false", "9, 6, 15, true", "8, 6, 15, false"})
        @DisplayName("успех — итог не меньше сложности")
        void successAtOrAboveDifficulty(int roll, int modifier, int difficulty, boolean success) {
            CheckResult result = resolver.resolve(new CheckRequest(null, modifier, difficulty), dice(roll));

            assertThat(result.total()).isEqualTo(roll + modifier);
            assertThat(result.success()).isEqualTo(success);
        }

        @Test
        @DisplayName("взрыв — крит, шаги расчёта и запись d10!")
        void criticalSteps() {
            CheckResult result = resolver.resolve(new CheckRequest("Взлом", 5, 20), dice(10, 7));

            assertThat(result.total()).isEqualTo(22);
            assertThat(result.success()).isTrue();
            assertThat(result.critical()).isTrue();
            assertThat(result.notation()).isEqualTo("d10!+5");
            assertThat(result.steps()).containsExactly(
                    "Бросок d10: 10 → +7 = 17 (10 — бросок ещё раз)",
                    "Модификатор +5 → 22",
                    "Сложность 20 → успех");
            assertThat(result.outcome()).isEqualTo("22 vs 20 — успех (крит)");
        }

        @Test
        @DisplayName("фамбл — провал, даже если итог выше сложности")
        void fumbleAlwaysFails() {
            CheckResult result = resolver.resolve(new CheckRequest(null, 20, 15), dice(1, 3));

            assertThat(result.total()).isEqualTo(21);
            assertThat(result.fumble()).isTrue();
            assertThat(result.success()).isFalse();
            assertThat(result.outcome()).isEqualTo("21 vs 15 — критический провал");
        }

        @Test
        @DisplayName("фамбл навыка — бросок по таблице его типа, результат в шагах")
        void fumbleTableStep() {
            CheckRequest request = CheckRequest.ofParts("Пистолеты",
                    List.of(new CheckRequest.Part("REF", 8), new CheckRequest.Part("Пистолеты", 4)), 15, "handgun");

            CheckResult result = resolver.resolve(request, dice(1, 5));

            assertThat(result.total()).isEqualTo(13);
            assertThat(result.steps()).containsExactly(
                    "Бросок d10: 1 — критический провал",
                    "REF +8, Пистолеты +4 → 13",
                    "Фамбл — провал независимо от итога",
                    "Таблица фамблов (боевая), d10 = 5: оружие выпало из рук");
        }

        @Test
        @DisplayName("части модификатора: шаг со слагаемыми и сумма")
        void partsStep() {
            CheckRequest request = CheckRequest.ofParts("Драка",
                    List.of(new CheckRequest.Part("REF", 6), new CheckRequest.Part("Драка", 0),
                            new CheckRequest.Part("Мод.", -2)), null, "brawling");

            CheckResult result = resolver.resolve(request, dice(5));

            assertThat(result.total()).isEqualTo(9);
            assertThat(result.notation()).isEqualTo("d10!+4");
            assertThat(result.steps()).contains("REF +6, Драка +0, Мод. -2 → 9");
        }

        @Test
        @DisplayName("без сложности — только итог")
        void noDifficulty() {
            CheckResult result = resolver.resolve(new CheckRequest(null, -2, null), dice(6));

            assertThat(result.success()).isNull();
            assertThat(result.notation()).isEqualTo("d10!-2");
            assertThat(result.outcome()).isEqualTo("4");
        }

        @Test
        @DisplayName("сложности книги: Легко 10 … Почти невозможно 30")
        void presets() {
            assertThat(resolver.getDifficultyPresets()).containsExactly(
                    java.util.Map.entry("Легко", 10), java.util.Map.entry("Средне", 15),
                    java.util.Map.entry("Сложно", 20), java.util.Map.entry("Очень сложно", 25),
                    java.util.Map.entry("Почти невозможно", 30));
        }
    }
}
