package app.charka.attack;

import app.charka.model.counters.strategy.health.DamageRequest;

import java.util.List;

/**
 * Одно попадание.
 *
 * @param where      куда попало, для цели без листа: её зону в расчёт не запишет счётчик здоровья
 * @param expression запись броска урона для лога
 * @param steps      как определены зона и урон
 * @param damage     что применить к цели; null — урон не бросается (например, «Drugs»), решает мастер
 */
public record AttackHit(String where, String expression, List<String> steps, DamageRequest damage) {

    public AttackHit {
        steps = List.copyOf(steps);
    }
}
