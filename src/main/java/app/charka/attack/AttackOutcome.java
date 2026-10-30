package app.charka.attack;

import app.charka.model.Character;

import java.util.List;

/**
 * Что вышло из одной проверки плана.
 *
 * @param steps      шаги сверх самой проверки
 * @param hits       попадания
 * @param scattered  заряд упал мимо: кого задело, выберет мастер; иначе null
 * @param stillCaught кто и так остался в зоне упавшего заряда: мастеру останется подтвердить
 * @param headline   короткий итог; null — итог из ран цели
 * @param hit        попала ли атака
 */
public record AttackOutcome(List<String> steps, List<TargetedHit> hits, AreaDamage scattered, List<Character> stillCaught,
                            String headline, boolean hit) {

    /**
     * @param target цель; null — цель без листа
     */
    public record TargetedHit(Character target, AttackHit hit) {
    }

    public AttackOutcome {
        steps = List.copyOf(steps);
        hits = List.copyOf(hits);
        stillCaught = List.copyOf(stillCaught);
    }

    public static AttackOutcome hits(List<String> steps, List<TargetedHit> hits, String headline, boolean hit) {
        return new AttackOutcome(steps, hits, null, List.of(), headline, hit);
    }

    public static AttackOutcome miss(List<String> steps, String headline) {
        return new AttackOutcome(steps, List.of(), null, List.of(), headline, false);
    }

    public static AttackOutcome scattered(List<String> steps, AreaDamage blast, List<Character> stillCaught, String headline) {
        return new AttackOutcome(steps, List.of(), blast, stillCaught, headline, false);
    }
}
