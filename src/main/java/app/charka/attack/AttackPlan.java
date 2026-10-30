package app.charka.attack;

import app.charka.check.CheckRequest;
import app.charka.model.Character;

import java.util.List;

/**
 * Что нужно бросить в этой атаке.
 *
 * @param title  заголовок итога: «Minami → Олежа»
 * @param checks проверки по порядку; пусто — без бросков, например, заряд на месте
 * @param blast  общий для всех целей урон, уже брошен; null — урон бросается по итогу проверки
 */
public record AttackPlan(String title, List<PlannedCheck> checks, AreaDamage blast) {

    public AttackPlan {
        checks = List.copyOf(checks);
    }

    /**
     * @param request проверка
     * @param roller  кто бросает; null — мастер за того, у кого нет листа
     * @param target  кого она касается; null — цель без листа или все сразу
     * @param intro   шаг перед броском; null — без него
     */
    public record PlannedCheck(CheckRequest request, Character roller, Character target, String intro) {
    }
}
