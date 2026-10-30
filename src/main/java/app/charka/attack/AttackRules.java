package app.charka.attack;

import app.charka.check.CheckResult;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.StatValue;

import java.util.Map;

/**
 * Правила атаки в системе: {@code GameSystem.getAttackRules()}. Ход один для всех атак: система строит план, ядро тратит ресурс,
 * бросает проверки плана, а по каждой система говорит, что вышло. Выстрел, зона огня, граната и заклинание различаются только планом.
 */
public interface AttackRules {

    /**
     * @param stats текущие статы атакующего
     * @return форма атаки; null — этим так не атакуют
     */
    AttackForm form(AttackSource source, Map<String, StatValue> stats);

    /**
     * Какие проверки нужны. Без расхода и применения урона.
     *
     * @throws IllegalArgumentException если атака невозможна
     */
    AttackPlan plan(AttackContext context, DiceRoller dice);

    /**
     * Расход на одну атаку; вызывается после {@link #plan}.
     *
     * @return итог для показа игроку; null — тратить нечего
     */
    String spend(AttackContext context);

    /**
     * Что вышло из проверки.
     *
     * @param planned проверка плана; null — в плане проверок нет
     * @param result  её итог; null — в плане проверок нет
     */
    AttackOutcome outcome(AttackContext context, AttackPlan plan, AttackPlan.PlannedCheck planned, CheckResult result, DiceRoller dice);

    /**
     * Попадание сохранённого заряда по цели, которую выбрал мастер: заряд упал мимо.
     */
    default AttackHit blastHit(AreaDamage blast, DiceRoller dice) {
        throw new IllegalArgumentException("У этой системы нет урона по площади");
    }
}
