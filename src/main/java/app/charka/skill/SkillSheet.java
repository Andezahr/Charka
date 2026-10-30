package app.charka.skill;

import java.util.List;

/**
 * Данные страницы навыков.
 *
 * @param heldRoles    роли персонажа
 * @param roleOptions  все роли системы для выбора
 * @param roleHint     почему нельзя взять новую роль; null — можно
 * @param groups       навыки по группам: сначала спецспособности ролей, затем по статам в порядке каталога
 * @param statsMissing у персонажа нет статов — итоги считаются только по уровню
 * @param pointsName   название очков развития; null — у системы нет прокачки за очки
 * @param points       очки персонажа; null — нет прокачки или счётчика очков
 */
public record SkillSheet(List<CharacterRole> heldRoles, List<RoleOption> roleOptions, String roleHint, List<Group> groups,
                         boolean statsMissing, String pointsName, Integer points) {

    /**
     * @param held      роль есть у персонажа
     * @param available роль можно отметить
     */
    public record RoleOption(String code, String name, boolean held, boolean available) {
    }

    /**
     * Система развивает навыки за очки, но у персонажа нет их счётчика.
     */
    public boolean isPointsMissing() {
        return pointsName != null && points == null;
    }

    /**
     * @param stat        стат группы; null, если у персонажа нет такого стата или это спецспособности
     * @param special     группа спецспособностей: у каждого навыка свой стат
     * @param customTypes навыки группы, которые добавляются со своим названием
     */
    public record Group(String title, StatValue stat, boolean special,
                        List<SkillDefinition> customTypes, List<Row> rows) {
    }

    /**
     * @param customId   id строки {@code CharacterSkill} у навыка со своим названием, иначе null
     * @param total      текущий стат + уровень
     * @param roleName   роль, которой принадлежит спецспособность, иначе null
     * @param nextCost   цена следующего уровня; null — прокачка недоступна или уровень максимальный
     * @param affordable очков хватает на следующий уровень
     */
    public record Row(String code, Long customId, String name, int level, int total,
                      String statName, int statValue, String roleName, Integer nextCost, boolean affordable) {

        /**
         * Якорь строки: после прокачки страница возвращается к ней.
         */
        public String getAnchor() {
            return anchor(code, customId);
        }

        public static String anchor(String code, Long customId) {
            return customId != null ? "skill-custom-" + customId : "skill-" + code;
        }
    }
}
