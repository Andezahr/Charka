package app.charka.attack;

import java.util.List;

/**
 * Итог атаки одной цепочкой шагов: попадание, урон, броня и раны цели.
 *
 * @param title    «Minami → Олежа»
 * @param outcome  короткий итог
 * @param hit      попала ли атака
 * @param steps    шаги по порядку
 * @param scattered заряд отклонился и упал мимо цели: кого задело, выбирает мастер; иначе null
 * @param detached итог не показать в строке предмета: предмет израсходован
 */
public record AttackResult(String title, String outcome, boolean hit, List<String> steps, AreaDamage scattered, boolean detached) {

    public AttackResult {
        steps = List.copyOf(steps);
    }

    public AttackResult(String title, String outcome, boolean hit, List<String> steps) {
        this(title, outcome, hit, steps, null, false);
    }

    public AttackResult withoutItemRow() {
        return new AttackResult(title, outcome, hit, steps, scattered, true);
    }
}
