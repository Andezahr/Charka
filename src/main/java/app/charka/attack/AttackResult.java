package app.charka.attack;

import java.util.List;

/**
 * Итог атаки одной цепочкой шагов: попадание, урон, броня и раны цели.
 *
 * @param title         «Minami → Олежа»
 * @param outcome       короткий итог
 * @param hit           попала ли атака
 * @param steps         шаги по порядку
 * @param pendingBlast  заряд упал мимо и ждёт решения мастера: id {@code PendingBlast}; иначе null
 * @param detached      итог показывается не в строке предмета: предмет израсходован или рядом нужно решение мастера
 */
public record AttackResult(String title, String outcome, boolean hit, List<String> steps, Long pendingBlast, boolean detached) {

    public AttackResult {
        steps = List.copyOf(steps);
    }

    public AttackResult(String title, String outcome, boolean hit, List<String> steps) {
        this(title, outcome, hit, steps, null, false);
    }

    public AttackResult withoutItemRow() {
        return new AttackResult(title, outcome, hit, steps, pendingBlast, true);
    }
}
