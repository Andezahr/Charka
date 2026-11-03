package app.charka.npc;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Что система выбросила для нового NPC; применяет {@link NpcService}, поэтому система не знает о хранении.
 *
 * @param roles      коды ролей
 * @param attributes attributes счётчиков: тип счётчика → значения
 * @param skills     уровни навыков по коду
 * @param items      снаряжение из справочника системы
 */
public record NpcSheet(Set<String> roles, Map<String, Map<String, Integer>> attributes, Map<String, Integer> skills, List<Gear> items) {

    /**
     * @param catalogKey строка справочника
     * @param action     действие над предметом сразу после выдачи, например, надеть; null — нет
     */
    public record Gear(String catalogKey, String action) {
    }
}
