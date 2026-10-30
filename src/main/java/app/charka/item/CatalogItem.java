package app.charka.item;

import java.util.Map;

/**
 * Готовый предмет из справочника системы ({@code GameSystem.getItemCatalog()}).
 *
 * @param key         код строки справочника
 * @param group       раздел справочника
 * @param name        название
 * @param typeCode    код типа предмета
 * @param cost        цена; если в книге диапазон — нижняя граница, диапазон в {@code description}
 * @param description примечание, может быть null
 * @param properties  значения полей типа в том виде, как их вводят в форме
 */
public record CatalogItem(String key, String group, String name, String typeCode, int cost, String description,
                          Map<String, String> properties) {

    public CatalogItem {
        properties = Map.copyOf(properties);
    }
}
