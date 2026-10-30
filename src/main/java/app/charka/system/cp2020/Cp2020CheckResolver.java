package app.charka.system.cp2020;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.check.CheckResult;
import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Проверка CP2020: 1d10 (10 взрывается, 1 — фамбл) + стат + навык + модификаторы против сложности.
 * Фамбл — всегда провал; что именно случилось, решает таблица фамблов ({@link Cp2020Fumbles}).
 */
public class Cp2020CheckResolver implements CheckResolver {

    private static final Map<String, Integer> PRESETS = new LinkedHashMap<>();

    static {
        // Easy, Average, Difficult, Very Difficult, Nearly Impossible
        PRESETS.put("Легко", 10);
        PRESETS.put("Средне", 15);
        PRESETS.put("Сложно", 20);
        PRESETS.put("Очень сложно", 25);
        PRESETS.put("Почти невозможно", 30);
    }

    @Override
    public CheckResult resolve(CheckRequest request, DiceRoller dice) {
        Cp2020Dice.Roll roll = Cp2020Dice.rollCheck(dice);
        int total = roll.total() + request.modifier();

        List<String> steps = new ArrayList<>();
        String rollStep = "Бросок d10: " + roll.describe();
        if (roll.exploded()) {
            rollStep += " (10 — бросок ещё раз)";
        }
        if (roll.fumble()) {
            rollStep += " — критический провал";
        }
        steps.add(rollStep);
        String modifierStep = CheckResolver.modifierStep(request, total);
        if (modifierStep != null) {
            steps.add(modifierStep);
        }

        Boolean success = CheckResolver.meets(total, request.difficulty());
        if (roll.fumble()) {
            success = Boolean.FALSE;
            steps.add("Фамбл — провал независимо от итога");
            steps.add(Cp2020Fumbles.roll(request.skillCode(), dice));
        } else if (success != null) {
            steps.add("Сложность " + request.difficulty() + " → " + (success ? "успех" : "провал"));
        }

        String notation = "d10!" + (request.modifier() != 0 ? CheckResolver.signed(request.modifier()) : "");
        return new CheckResult(notation, steps, total, request.difficulty(), success, roll.exploded(), roll.fumble());
    }

    @Override
    public Map<String, Integer> getDifficultyPresets() {
        return PRESETS;
    }
}
