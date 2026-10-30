package app.charka.skill;

import java.util.List;

/**
 * Данные страницы навыков.
 *
 * @param role         выбранная роль, null — не выбрана
 * @param roles        роли системы
 * @param groups       навыки по группам: сначала спецспособность, затем по статам в порядке каталога
 * @param statsMissing у персонажа нет статов (удалён счётчик) — итоги считаются только по уровню
 */
public record SkillSheet(CharacterRole role, List<CharacterRole> roles, List<Group> groups, boolean statsMissing) {

    /**
     * @param stat        стат группы; null, если у персонажа нет такого стата или это спецспособности
     * @param special     группа спецспособностей: у каждого навыка свой стат
     * @param customTypes навыки группы, которые добавляются со своим названием
     */
    public record Group(String title, StatValue stat, boolean special,
                        List<SkillDefinition> customTypes, List<Row> rows) {
    }

    /**
     * @param customId id строки {@code CharacterSkill} у навыка со своим названием, иначе null
     * @param total    текущий стат + уровень
     */
    public record Row(String code, Long customId, String name, int level, int total,
                      String statName, int statValue) {
    }
}
