package app.charka.check;

import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Универсальная проверка: d20 + модификатор против сложности, без критов и провалов.
 */
public class GenericCheckResolver implements CheckResolver {

    static final int SIDES = 20;

    private static final Map<String, Integer> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Легко", 10);
        PRESETS.put("Средне", 15);
        PRESETS.put("Трудно", 20);
    }

    @Override
    public CheckResult resolve(CheckRequest request, DiceRoller dice) {
        int roll = dice.roll(SIDES);
        int total = roll + request.modifier();

        List<String> steps = new ArrayList<>();
        steps.add("Бросок d" + SIDES + ": " + roll);
        if (request.modifier() != 0) {
            steps.add("Модификатор " + CheckResolver.signed(request.modifier()) + " → " + total);
        }
        Boolean success = CheckResolver.meets(total, request.difficulty());
        if (success != null) {
            steps.add("Сложность " + request.difficulty() + " → " + (success ? "успех" : "провал"));
        }
        String notation = "d" + SIDES + (request.modifier() != 0 ? CheckResolver.signed(request.modifier()) : "");
        return new CheckResult(notation, steps, total, request.difficulty(), success, false, false);
    }

    @Override
    public Map<String, Integer> getDifficultyPresets() {
        return PRESETS;
    }
}
