package app.charka.attack;

import app.charka.check.CheckResult;
import app.charka.dice.DiceExpression;
import app.charka.dice.DiceResult;
import app.charka.model.Character;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.StatValue;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
     * Чем персонаж может атаковать без предмета: кулаки, заклинания.
     */
    default List<AttackSource> abilities(Character attacker) {
        return List.of();
    }

    /**
     * Попадание сохранённого заряда по цели, которую выбрал мастер: заряд упал мимо.
     */
    default AttackHit blastHit(AreaDamage blast, DiceRoller dice) {
        throw new IllegalArgumentException("У этой системы нет урона по площади");
    }

    /**
     * Очередной бросок длящегося эффекта: начался новый раунд.
     *
     * @param expression бросок из {@link LingeringEffect#damage}
     * @param data       данные из {@link LingeringEffect#data}
     */
    default AttackHit effectHit(String expression, Map<String, String> data, DiceRoller dice) {
        DiceExpression parsed = DiceExpression.parse(expression);
        DiceResult result = DiceResult.of(parsed, IntStream.range(0, parsed.count()).map(i -> dice.roll(parsed.sides())).boxed().toList());
        return new AttackHit("тело", expression, List.of("Урон " + expression + ": " + result.details()),
                DamageRequest.builder().amount(result.total()).build());
    }
}
