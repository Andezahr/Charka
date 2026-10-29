package app.charka.check;

import java.util.List;

/**
 * Итог проверки с пошаговым расчётом (как {@code HealthEvent} у урона) — для единого UI «как посчитано».
 *
 * @param notation   запись броска для лога: {@code d10!+5}, {@code d20-1}
 * @param steps      шаги расчёта по порядку
 * @param total      итог (бросок + модификатор)
 * @param difficulty сложность, null — без сложности
 * @param success    успех; null, если сложность не задана
 * @param critical   критический успех по правилам системы
 * @param fumble     критический провал по правилам системы
 */
public record CheckResult(String notation, List<String> steps, int total, Integer difficulty,
                          Boolean success, boolean critical, boolean fumble) {

    public CheckResult {
        steps = List.copyOf(steps);
    }

    /**
     * Короткий итог для лога и заголовка: «17 vs 15 — успех», «1 — критический провал».
     */
    public String outcome() {
        StringBuilder sb = new StringBuilder().append(total);
        if (difficulty != null) {
            sb.append(" vs ").append(difficulty);
        }
        if (fumble) {
            sb.append(" — критический провал");
        } else if (success != null) {
            sb.append(success ? " — успех" : " — провал");
        }
        if (critical && !fumble) {
            sb.append(" (крит)");
        }
        return sb.toString();
    }
}
