package app.charka.skill;

/**
 * Уровень навыка, который задаёт не сам персонаж, а то, что на нём (CP2020: чип). Заменяет природный уровень в проверках.
 *
 * @param source откуда, коротко: «чип»
 * @param level  уровень вместо природного
 */
public record SkillOverride(String source, int level) {

    /** Для строки навыка: «чип +2» */
    public String label() {
        return source + " +" + level;
    }
}
