package app.charka.skill;

/**
 * Противник в проверке навыка.
 *
 * @param characterId персонаж той же кампании; null — противник без листа, всё его значение в {@code modifier}
 * @param code        код навыка персонажа; не используется, если задан {@code skillId}
 * @param skillId     id строки навыка со своим названием
 * @param name        имя противника без листа, может быть пустым
 * @param modifier    модификатор персонажа либо «стат + навык» противника без листа
 */
public record SkillOpponent(Long characterId, String code, Long skillId, String name, int modifier) {
}
