package app.charka.attack;

import app.charka.model.Character;

import java.util.List;

/**
 * M-17: кто стоит на линии огня между стрелком и целью. Знает поле боя (карта и фишки), правила атаки — нет:
 * что будет со стоящим, решает {@link AttackRules#strayHit}.
 */
public interface LineOfFire {

    /**
     * @return стоящие на линии между центрами клеток, от стрелка к цели; пусто — боя на карте нет или линия свободна
     */
    List<Bystander> between(Character attacker, Character target);

    /**
     * @param meters расстояние от стрелка
     */
    record Bystander(Character character, int meters) {
    }
}
