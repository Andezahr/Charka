package app.charka.check;

import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.Map;

/**
 * Правила проверки игровой системы ({@code GameSystem.getCheckResolver()}).
 * Резолвер без состояния: кубики передаёт вызывающий, чтобы в тестах подставлять известные броски.
 */
public interface CheckResolver {

    CheckResult resolve(CheckRequest request, DiceRoller dice);

    /**
     * Типовые сложности для подсказок в форме: название → значение.
     */
    default Map<String, Integer> getDifficultyPresets() {
        return Map.of();
    }

    /**
     * Успех, если итог не меньше сложности; без сложности — null.
     */
    static Boolean meets(int total, Integer difficulty) {
        return difficulty == null ? null : total >= difficulty;
    }

    static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }
}
