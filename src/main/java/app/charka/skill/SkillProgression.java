package app.charka.skill;

import app.charka.model.Character;
import app.charka.model.CharacterSkill;

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
     * Цена с учётом остальных навыков персонажа; по умолчанию они не влияют.
     *
     * @param skill строка навыка; null, если её ещё нет
     */
    default int cost(Character character, SkillDefinition definition, CharacterSkill skill, int level) {
        return cost(definition, level);
    }

    /**
     * Почему персонаж пока не может повышать навык; null — может.
     */
    default String blockedReason(Character character, SkillDefinition definition) {
        return null;
    }

    /**
     * Сколько очков у персонажа; null — у него нет счётчика очков.
     */
    Integer balance(Character character);

    /**
     * Списывает очки. Хватает ли их, проверяет вызывающий.
     */
    void spend(Character character, int amount);
}
