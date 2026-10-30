package app.charka.skill;

import app.charka.model.Character;

/**
 * Правила развития навыков системы: чем платить и сколько стоит следующий уровень.
 * Методы с персонажем читают его счётчики — вызывать в транзакции.
 */
public interface SkillProgression {

    /**
     * Краткое название очков для интерфейса: «IP».
     */
    String getPointsName();

    /**
     * Цена повышения навыка с уровня {@code level} на следующий.
     */
    int cost(SkillDefinition definition, int level);

    /**
     * Сколько очков у персонажа; null — у него нет счётчика очков.
     */
    Integer balance(Character character);

    /**
     * Списывает очки. Хватает ли их, проверяет вызывающий.
     */
    void spend(Character character, int amount);
}
