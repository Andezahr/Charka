package app.charka.attack;

import java.util.List;

/**
 * Итог атаки одной цепочкой шагов: попадание, урон, броня и раны цели.
 *
 * @param title   «Minami → Олежа»
 * @param outcome короткий итог
 * @param hit     попала ли атака
 * @param steps   шаги по порядку
 */
public record AttackResult(String title, String outcome, boolean hit, List<String> steps) {

    public AttackResult {
        steps = List.copyOf(steps);
    }
}
