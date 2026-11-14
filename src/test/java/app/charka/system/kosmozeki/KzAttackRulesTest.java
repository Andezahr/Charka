package app.charka.system.kosmozeki;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KZT-01: сложность стрельбы Космозэков без Spring — DIF + модификатор − пули, «Сложность вдвое».
 * Числа — примеры из RULES_KOSMOZEKI.md; итог атаки целиком — в {@code KzSystemTest}.
 */
@DisplayName("Космозэки: сложность атаки")
class KzAttackRulesTest {

    private static KzAttackRules.Modifiers none() {
        return new KzAttackRules.Modifiers(0, List.of());
    }

    @Test
    @DisplayName("Пример правил: винтовка DIF 50, 30 пуль → сложность 20")
    void rifleExample() {
        assertThat(KzAttackRules.difficulty(50, none(), 30, false).value()).isEqualTo(20);
    }

    @Test
    @DisplayName("Модификатор прибавляется: пистолет DIF 30, дым +10, 2 пули → 38")
    void modifier() {
        KzAttackRules.Modifiers smoke = new KzAttackRules.Modifiers(10, List.of("Дым +10"));
        assertThat(KzAttackRules.difficulty(30, smoke, 2, false).value()).isEqualTo(38);
    }

    @ParameterizedTest(name = "DIF {0}, мод {1}, пуль {2} → {3}")
    @CsvSource({
            // база пополам и потом остальное: 15 + 20 − 1 = 34; итог пополам: 49 / 2 = 24 → берём 24
            "30, 20, 1, 24",
            // минус: 25 − 30 − 0 = −5; итог пополам: 20 / 2 = 10 → −5
            "50, -30, 0, -5"})
    @DisplayName("«Сложность вдвое» — как выгоднее стрелку (ответ автора 2026-11-07)")
    void halve(int base, int modifier, int rounds, int expected) {
        KzAttackRules.Modifiers mods = new KzAttackRules.Modifiers(modifier, List.of("мастер"));
        assertThat(KzAttackRules.difficulty(base, mods, rounds, true).value()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Шаги расчёта называют пули")
    void step() {
        assertThat(KzAttackRules.difficulty(30, none(), 2, false).step()).isEqualTo("Сложность: DIF 30 − 2 пуль = 28");
    }
}
