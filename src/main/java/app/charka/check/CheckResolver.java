package app.charka.check;

import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.Map;
import java.util.stream.Collectors;

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
     * Победитель противопоставленной проверки: фамбл проигрывает любому другому броску, иначе больший итог.
     * При равенстве побеждает защищающийся, то есть {@code opponent}.
     */
    default OpposedCheckResult.Winner opposedWinner(CheckResult initiator, CheckResult opponent) {
        if (initiator.fumble() && opponent.fumble()) {
            return OpposedCheckResult.Winner.NONE;
        }
        if (initiator.fumble() != opponent.fumble()) {
            return initiator.fumble() ? OpposedCheckResult.Winner.OPPONENT : OpposedCheckResult.Winner.INITIATOR;
        }
        return initiator.total() > opponent.total() ? OpposedCheckResult.Winner.INITIATOR : OpposedCheckResult.Winner.OPPONENT;
    }

    /**
     * Успех, если итог не меньше сложности; без сложности — null.
     */
    static Boolean meets(int total, Integer difficulty) {
        return difficulty == null ? null : total >= difficulty;
    }

    /**
     * Шаг расчёта с модификатором: «REF +8, Пистолеты +4 → 19», для одного числа — «Модификатор +5 → 12».
     * Null, если прибавлять нечего.
     */
    static String modifierStep(CheckRequest request, int total) {
        if (request.parts().isEmpty()) {
            return request.modifier() == 0 ? null : "Модификатор " + signed(request.modifier()) + " → " + total;
        }
        return request.parts().stream()
                .map(part -> part.name() + " " + signed(part.value()))
                .collect(Collectors.joining(", ")) + " → " + total;
    }

    static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }
}
