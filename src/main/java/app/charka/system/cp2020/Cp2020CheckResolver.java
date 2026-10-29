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
 * Фамбл — всегда провал; что именно случилось, решает таблица фамблов (K-06).
 */
public class Cp2020CheckResolver implements CheckResolver {

    private static final Map<String, Integer> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Easy", 10);
        PRESETS.put("Average", 15);
        PRESETS.put("Difficult", 20);
        PRESETS.put("Very Difficult", 25);
        PRESETS.put("Nearly Impossible", 30);
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
        if (request.modifier() != 0) {
            steps.add("Модификатор " + CheckResolver.signed(request.modifier()) + " → " + total);
        }

        Boolean success = CheckResolver.meets(total, request.difficulty());
        if (roll.fumble()) {
            success = Boolean.FALSE;
            steps.add("Фамбл — провал независимо от итога");
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
