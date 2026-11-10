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
 * @param total         итог броска атакующего (для анимации броска); null — броска нет
 * @param difficulty    сложность этого броска; null — без сложности
 */
public record AttackResult(String title, String outcome, boolean hit, List<String> steps, Long pendingBlast, boolean detached,
                           Integer total, Integer difficulty) {

    public AttackResult {
        steps = List.copyOf(steps);
    }

    public AttackResult(String title, String outcome, boolean hit, List<String> steps, Long pendingBlast, boolean detached) {
        this(title, outcome, hit, steps, pendingBlast, detached, null, null);
    }

    public AttackResult(String title, String outcome, boolean hit, List<String> steps) {
        this(title, outcome, hit, steps, null, false);
    }

    public AttackResult withoutItemRow() {
        return new AttackResult(title, outcome, hit, steps, pendingBlast, true, total, difficulty);
    }

    public AttackResult withRoll(Integer total, Integer difficulty) {
        return new AttackResult(title, outcome, hit, steps, pendingBlast, detached, total, difficulty);
    }
}
