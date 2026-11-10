package app.charka.attack;

import app.charka.check.CheckResult;
import app.charka.dice.DiceExpression;
import app.charka.dice.DiceResult;
import app.charka.model.Character;
import app.charka.model.Item;
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
    /**
     * A-05: модификатор атаки за то, что в раунде уже сделано {@code done} действий; ставится в диалоге сам.
     *
     * @return код варианта модификатора; null — штрафа нет
     */
    default String actionOption(int done) {
        return null;
    }

    /**
     * R-07: из чего складывается бросок атаки до модификаторов — строка у оружия на листе («Пистолеты 5 + REF 6 + WA 1 = 12»),
     * чтобы не считать в уме. null — система такой строки не даёт.
     */
    default String attackBase(Character attacker, AttackSource source, AttackForm form, Map<String, StatValue> stats) {
        return null;
    }

    default List<AttackSource> abilities(Character attacker) {
        return List.of();
    }

    /**
     * Можно ли выбрать этот предмет целью атаки: машина с прочностью.
     */
    default boolean isObjectTarget(Item item) {
        return false;
    }

    /**
     * Попадание по цели-предмету: урон в его прочность.
     *
     * @return итог для показа: сколько прочности осталось
     */
    default String damageObject(Item target, AttackHit hit) {
        throw new IllegalArgumentException("Эта система не атакует предметы");
    }

    /**
     * M-17: атака по цели не попала, а между стрелком и целью кто-то стоит. Попадёт ли промах в него.
     *
     * @param missed    итог проверки, которая не попала
     * @param bystander первый стоящий на линии огня от стрелка
     * @param meters    расстояние от стрелка до него
     * @return попадание по стоящему с шагами расчёта; пусто — пуля ушла мимо всех или правило к этой атаке не относится
     */
    default java.util.Optional<AttackOutcome> strayHit(AttackContext context, CheckResult missed, Character bystander, int meters, DiceRoller dice) {
        return java.util.Optional.empty();
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
