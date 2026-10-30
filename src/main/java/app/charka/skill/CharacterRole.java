package app.charka.skill;

/**
 * Роль/класс персонажа из правил системы.
 *
 * @param code             код, хранится в {@code Character.role}
 * @param name             название для интерфейса
 * @param specialSkillCode код спецспособности роли в каталоге навыков, null — нет
 */
public record CharacterRole(String code, String name, String specialSkillCode) {
}
