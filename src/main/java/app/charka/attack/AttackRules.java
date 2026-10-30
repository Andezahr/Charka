package app.charka.attack;

import app.charka.check.CheckRequest;
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
     * Расход на одну атаку. Вызывается после успешного {@link #toHit}.
     *
     * @return итог для показа игроку
     */
    String spend(Item weapon);

    /**
     * Попадания успешной атаки.
     */
    List<AttackHit> hits(Item weapon, AttackRequest request, DiceRoller dice);
}
