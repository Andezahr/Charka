package app.charka.attack;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.StatValue;

import java.util.List;
import java.util.Map;

/**
 * Правила атаки оружием в системе: {@code GameSystem.getAttackRules()}. Ход атаки и применение урона — {@link AttackService}.
 */
public interface AttackRules {

    /**
     * @return форма атаки; null — этим предметом так не атакуют
     */
    AttackForm form(Item weapon);

    /**
     * Проверка на попадание.
     *
     * @throws IllegalArgumentException если атака невозможна
     */
    CheckRequest toHit(Character attacker, Item weapon, AttackRequest request, Map<String, StatValue> stats);

    /**
     * Расход на одну атаку. Вызывается после проверок возможности атаки.
     *
     * @return итог для показа игроку
     */
    String spend(Item weapon, AttackRequest request);

    /**
     * Попадания успешной атаки.
     *
     * @param check итог проверки на попадание
     */
    List<AttackHit> hits(Item weapon, AttackRequest request, CheckResult check, DiceRoller dice);

    /**
     * Что ещё случилось при критическом провале, например, оружие заклинило.
     *
     * @return шаги расчёта
     */
    default List<String> onFumble(Item weapon, DiceRoller dice) {
        return List.of();
    }

    /**
     * Режим без броска атакующего: цели в зоне сами бросают против него.
     */
    default boolean isZoneFire(AttackRequest request) {
        return false;
    }

    /**
     * Спасбросок цели в зоне огня.
     *
     * @param target цель; null — цель без листа, её итог в {@code request.modifier()}
     * @param stats  текущие статы цели
     * @throws IllegalArgumentException если такой огонь невозможен
     */
    default CheckRequest zoneSave(Character target, Item weapon, AttackRequest request, Map<String, StatValue> stats) {
        throw new IllegalArgumentException("Такого режима огня нет");
    }

    /**
     * Попадания по цели, которая не прошла спасбросок в зоне огня.
     */
    default List<AttackHit> zoneHits(Item weapon, AttackRequest request, DiceRoller dice) {
        return List.of();
    }
}
