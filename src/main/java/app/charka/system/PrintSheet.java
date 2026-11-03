package app.charka.system;

import java.util.List;

/**
 * Что система печатает на бумажном листе (A-12) сверх общего (имя, роли, снаряжение, деньги): статы, броня, навыки
 * и трек здоровья для пометок карандашом.
 *
 * @param blocks разделы со строками «REF · 8»
 * @param track  трек здоровья; null — у системы его нет
 */
public record PrintSheet(List<Reference> blocks, Track track) {

    public static final PrintSheet EMPTY = new PrintSheet(List.of(), null);

    /**
     * @param filled сколько клеток уже занято (урон сейчас)
     * @param note   что делать при ранении: спасброски
     */
    public record Track(String title, List<Group> groups, int filled, String note) {
    }

    /** Уровень трека: «Серьёзное · −2 REF», 4 клетки */
    public record Group(String label, int boxes) {
    }
}
