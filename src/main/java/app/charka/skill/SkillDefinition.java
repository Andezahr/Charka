package app.charka.skill;

/**
 * Навык из каталога системы.
 *
 * @param code           код, хранится в {@code CharacterSkill.skillCode}
 * @param name           название для интерфейса
 * @param statCode       код стата, с которым складывается навык (см. {@code GameSystem.getCurrentStats})
 * @param costMultiplier множитель стоимости развития
 * @param special        спецспособность роли: показывается только у своей роли
 * @param custom         у персонажа может быть несколько таких навыков со своими названиями (языки, «Эксперт: …»)
 */
public record SkillDefinition(String code, String name, String statCode, int costMultiplier,
                              boolean special, boolean custom) {
}
