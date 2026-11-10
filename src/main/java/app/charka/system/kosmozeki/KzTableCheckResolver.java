package app.charka.system.kosmozeki;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.check.CheckResult;
import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.ArrayList;
import java.util.List;

/**
 * KF-09: проверка Космозэков вне боя — 1к12 + модификатор против сложности, которую мастер называет и вписывает числом
 * (ответ автора 2026-11-08). Выпало не меньше сложности — успех, как у к100. Готовых ступеней сложности нет.
 * Критов у к12 автор не называл — их нет. Попадание в бою — по-прежнему к100 ({@link KzCheckResolver}).
 */
public class KzTableCheckResolver implements CheckResolver {

    public static final int DIE = 12;

    @Override
    public CheckResult resolve(CheckRequest request, DiceRoller dice) {
        int roll = dice.roll(DIE);
        int total = roll + request.modifier();
        List<String> steps = new ArrayList<>();
        steps.add("Бросок 1к12: " + roll);
        String modifierStep = CheckResolver.modifierStep(request, total);
        if (modifierStep != null) {
            steps.add(modifierStep);
        }
        Boolean success = CheckResolver.meets(total, request.difficulty());
        if (success != null) {
            steps.add("Сложность " + request.difficulty() + " → " + (success ? "успех" : "провал"));
        }
        String notation = "1d12" + (request.modifier() != 0 ? CheckResolver.signed(request.modifier()) : "");
        return new CheckResult(notation, steps, total, request.difficulty(), success, false, false);
    }
}
