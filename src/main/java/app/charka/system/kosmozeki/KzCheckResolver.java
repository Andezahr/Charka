package app.charka.system.kosmozeki;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.check.CheckResult;
import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Проверка Космозэков: к100 (два к10 — десятки и единицы, «0 и 0» = 100) против сложности; выпало не меньше — успех.
 * 1 — критический провал (что случилось, решает мастер), 100 — критический успех.
 */
public class KzCheckResolver implements CheckResolver {

    public static final int CRIT = 100;
    public static final int FUMBLE = 1;

    /** HOMEBREW: в правилах сложностей вне боя нет — подсказки для мастера, шагом пистолета (DIF 30) */
    private static final Map<String, Integer> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Легко", 20);
        PRESETS.put("Средне", 40);
        PRESETS.put("Трудно", 60);
        PRESETS.put("Очень трудно", 80);
    }

    /**
     * Бросок к100 двумя к10.
     *
     * @param tens  кубик десятков, 0–9
     * @param units кубик единиц, 0–9
     * @param value итог 1–100
     */
    public record D100(int tens, int units, int value) {

        public String describe() {
            return "к100: " + tens + "|" + units + " → " + value;
        }
    }

    public static D100 roll(DiceRoller dice) {
        int tens = dice.roll(10) % 10;
        int units = dice.roll(10) % 10;
        int value = tens * 10 + units;
        return new D100(tens, units, value == 0 ? 100 : value);
    }

    @Override
    public CheckResult resolve(CheckRequest request, DiceRoller dice) {
        D100 roll = roll(dice);
        int total = roll.value() + request.modifier();
        List<String> steps = new ArrayList<>();
        String rollStep = "Бросок " + roll.describe();
        boolean fumble = roll.value() == FUMBLE;
        boolean critical = roll.value() == CRIT;
        if (fumble) {
            rollStep += " — критический провал: что-то плохое, решает мастер";
        } else if (critical) {
            rollStep += " — крит";
        }
        steps.add(rollStep);
        String modifierStep = CheckResolver.modifierStep(request, total);
        if (modifierStep != null) {
            steps.add(modifierStep);
        }
        Boolean success = CheckResolver.meets(total, request.difficulty());
        if (fumble && request.difficulty() != null) {
            success = Boolean.FALSE;
        } else if (critical && request.difficulty() != null) {
            success = Boolean.TRUE;
        }
        if (success != null) {
            steps.add("Сложность " + request.difficulty() + " → " + (success ? "успех" : "провал"));
        }
        String notation = "d100" + (request.modifier() != 0 ? CheckResolver.signed(request.modifier()) : "");
        return new CheckResult(notation, steps, total, request.difficulty(), success, critical, fumble);
    }

    @Override
    public Map<String, Integer> getDifficultyPresets() {
        return PRESETS;
    }
}
