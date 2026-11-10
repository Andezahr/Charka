package app.charka.attack;

import app.charka.model.Character;
import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.List;

/**
 * M-23: взрыв меняет поле боя — ломает слабые стены, двери, окна и предметы, подрывает бочки и баллоны.
 * Урон людям от подорванного считает атака ({@link AttackService}): здесь — только карта и кто в зоне.
 */
public interface MapBlast {

    /**
     * Взрыв в клетке персонажа на карте идущего боя (граната упала на цель).
     *
     * @param amount урон взрыва
     * @param radius радиус, м
     * @return что изменилось; пусто — боя на карте нет или персонаж не на карте
     */
    Result explodeAt(Character center, int amount, int radius, DiceRoller dice);

    /**
     * Подорвать взрывоопасный предмет в клетке: попали в бочку.
     *
     * @param level M-22: уровень карты
     *
     * @throws IllegalArgumentException если в клетке нечему взрываться
     */
    Result detonate(Long campaignId, int level, int x, int y, DiceRoller dice);

    /**
     * Подорванный предмет.
     *
     * @param expression бросок урона
     * @param caught     кто в радиусе и не за стеной
     */
    record Detonation(String label, String expression, int amount, int radius, List<String> steps, List<Character> caught) {
    }

    /**
     * @param steps       что сломано
     * @param detonations подорванное, по порядку цепочки
     */
    record Result(List<String> steps, List<Detonation> detonations) {

        public static final Result NONE = new Result(List.of(), List.of());
    }
}
