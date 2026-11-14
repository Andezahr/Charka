package app.charka.system.kosmozeki;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.dice.DiceRoller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KZT-01: проверка Космозэков без Spring — к100 двумя к10, 1 и 100 — криты (RULES_KOSMOZEKI.md).
 */
@DisplayName("Космозэки: проверка к100")
class KzCheckResolverTest {

    private final KzCheckResolver resolver = new KzCheckResolver();

    /** Кубики по очереди; к10 даёт 1–10, 10 читается как 0 */
    static DiceRoller dice(int... rolls) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int roll : rolls) {
            queue.add(roll);
        }
        return sides -> queue.removeFirst();
    }

    private static CheckRequest request(int modifier, Integer difficulty) {
        return new CheckRequest("Проверка", modifier, difficulty, List.of(), null);
    }

    @ParameterizedTest(name = "десятки {0}, единицы {1} → {2}")
    @CsvSource({"10, 10, 100", "10, 1, 1", "4, 7, 47", "10, 5, 5", "9, 10, 90"})
    @DisplayName("к100 из двух к10: «0 и 0» — 100")
    void d100(int tens, int units, int expected) {
        assertThat(KzCheckResolver.roll(dice(tens, units)).value()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Выпало не меньше сложности — успех, меньше — провал")
    void meetsDifficulty() {
        assertThat(resolver.resolve(request(0, 47), dice(4, 7)).success()).isTrue();
        assertThat(resolver.resolve(request(0, 48), dice(4, 7)).success()).isFalse();
    }

    @Test
    @DisplayName("Модификатор прибавляется к броску")
    void modifier() {
        CheckResult result = resolver.resolve(request(10, 50), dice(4, 1));
        assertThat(result.total()).isEqualTo(51);
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("1 — критический провал даже с большим модификатором")
    void fumble() {
        CheckResult result = resolver.resolve(request(90, 20), dice(10, 1));
        assertThat(result.fumble()).isTrue();
        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("100 — критический успех даже против большей сложности")
    void critical() {
        CheckResult result = resolver.resolve(request(-50, 120), dice(10, 10));
        assertThat(result.critical()).isTrue();
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("Без сложности успеха нет — только итог")
    void noDifficulty() {
        assertThat(resolver.resolve(request(0, null), dice(10, 10)).success()).isNull();
    }
}
