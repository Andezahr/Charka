package app.charka.system.cp2020;

import app.charka.check.CheckRequest;
import app.charka.encounter.Initiative;
import app.charka.model.Character;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.StatValue;

import java.util.Map;
import java.util.Optional;

/**
 * Инициатива (книга, с. 97): 1D10 + текущий REF + Combat Sense у Соло, бросается каждый раунд.
 * Это не проверка навыка: дополнительного кубика на 10 и фамбла на 1 нет. Ускорители появятся с киберware (G-01).
 */
final class Cp2020Initiative {

    private static final String REF = "ref";

    private Cp2020Initiative() {
    }

    static Initiative roll(Character character, Map<String, StatValue> stats, DiceRoller dice) {
        int ref = stats.containsKey(REF) ? stats.get(REF).value() : 0;
        int roll = dice.d10();
        Optional<CheckRequest.Part> combatSense = Cp2020SkillRules.combatSense(character);
        int total = ref + roll + combatSense.map(CheckRequest.Part::value).orElse(0);
        String details = "REF " + ref + " + d10 " + roll
                + combatSense.map(part -> " + " + part.name() + " " + part.value()).orElse("")
                + " = " + total;
        return new Initiative(total, details);
    }
}
