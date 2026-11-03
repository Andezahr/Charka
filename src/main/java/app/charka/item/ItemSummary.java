package app.charka.item;

import java.util.List;
import java.util.Map;

/**
 * Краткая строка предмета на листе: только то, что нужно за столом. Всё остальное — в панели предмета.
 *
 * @param main  главное значение, крупно: заряд оружия, SP брони; null — нет
 * @param lines остальные значения
 */
public record ItemSummary(String main, List<String> lines) {

    public ItemSummary {
        lines = List.copyOf(lines);
    }

    /** Все поля типа: для типов, про которые система не сказала, что важно */
    public static ItemSummary allFields(ItemTypeDefinition type, Map<String, String> properties) {
        return new ItemSummary(null, type.describe(properties).stream().map(ItemTypeDefinition.Line::text).toList());
    }
}
