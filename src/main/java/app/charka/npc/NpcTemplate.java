package app.charka.npc;

/**
 * Шаблон быстрого создания NPC в системе.
 *
 * @param code  код шаблона
 * @param label название; им же называется NPC, если имя не задано
 */
public record NpcTemplate(String code, String label) {
}
