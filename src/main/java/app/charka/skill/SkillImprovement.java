package app.charka.skill;

/**
 * Итог повышения навыка за очки.
 *
 * @param name  название навыка
 * @param level новый уровень
 * @param cost  потрачено очков
 */
public record SkillImprovement(String name, int level, int cost) {
}
