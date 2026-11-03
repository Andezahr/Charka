package app.charka.attack;

import app.charka.model.counters.strategy.health.DamageRequest;

import java.util.List;

/**
 * Одно попадание.
 *
 * @param where      куда попало, для цели без листа: её зону в расчёт не запишет счётчик здоровья
 * @param expression запись броска урона для лога
 * @param steps      как определены зона и урон
 * @param damage     что применить к цели; null — урон не бросается (например, «Drugs»), решает мастер, если нет эффекта ниже
 * @param save       спасбросок цели от попадания; null — его нет
 * @param effects    что остаётся на цели на следующие раунды
 * @param clears     какие эффекты снять с самого атакующего: вырвался из захвата
 */
public record AttackHit(String where, String expression, List<String> steps, DamageRequest damage, HitSave save, List<LingeringEffect> effects, List<String> clears) {

    /**
     * @param code     код спасброска системы
     * @param modifier прибавка к числу спасброска
     */
    public record HitSave(String code, int modifier) {
    }

    public AttackHit {
        steps = List.copyOf(steps);
        effects = List.copyOf(effects);
        clears = List.copyOf(clears);
    }

    public AttackHit(String where, String expression, List<String> steps, DamageRequest damage, HitSave save, List<LingeringEffect> effects) {
        this(where, expression, steps, damage, save, effects, List.of());
    }

    public AttackHit(String where, String expression, List<String> steps, DamageRequest damage) {
        this(where, expression, steps, damage, null, List.of());
    }

    /** Попадание без урона, но с правилами: мастеру решать нечего */
    public boolean hasEffect() {
        return save != null || !effects.isEmpty() || !clears.isEmpty();
    }
}
