package app.charka.system.cp2020;

import app.charka.item.CatalogItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Справочник брони: таблица книги (с. 67, значения прочитаны с изображения страницы). Жёсткая или мягкая — таблица FNFF (с. 101).
 */
public final class Cp2020Armors {

    private static final String GROUP = "Броня";
    private static final String HARD = Cp2020ItemTypes.HARD;
    private static final String SOFT = Cp2020ItemTypes.SOFT;

    private static final List<Cp2020Location> HEAD = List.of(Cp2020Location.HEAD);
    private static final List<Cp2020Location> TORSO = List.of(Cp2020Location.TORSO);
    private static final List<Cp2020Location> LEGS = List.of(Cp2020Location.R_LEG, Cp2020Location.L_LEG);
    private static final List<Cp2020Location> TORSO_ARMS = List.of(Cp2020Location.TORSO, Cp2020Location.R_ARM, Cp2020Location.L_ARM);
    private static final List<Cp2020Location> WHOLE_BODY = List.of(Cp2020Location.values());

    private static final String MAYBE_LEGS = "Книга: руки, торс, возможно ноги; ноги отметьте в правке предмета";

    public static final List<CatalogItem> CATALOG = List.of(
            // HOMEBREW: в таблице FNFF (с. 101) обычной одежды нет, считаем мягкой; цена в книге «Varies»
            edgeWeak(armor("cloth", "Одежда, кожа", 0, 0, 0, SOFT, TORSO_ARMS, MAYBE_LEGS + ". Цена разная")),
            armor("heavy_leather", "Тяжёлая кожа", 4, 0, 50, SOFT, TORSO_ARMS, MAYBE_LEGS),
            edgeWeak(armor("kevlar_vest", "Кевларовая футболка, жилет", 10, 0, 90, SOFT, TORSO, null)),
            armor("steel_helmet", "Стальной шлем", 14, 0, 20, HARD, HEAD, null),
            edgeWeak(armor("light_jacket", "Лёгкая бронекуртка", 14, 0, 150, SOFT, TORSO_ARMS, null)),
            armor("medium_jacket", "Средняя бронекуртка", 18, 1, 200, SOFT, TORSO_ARMS, null),
            edgeWeak(armor("flak_vest", "Бронежилет Flak", 20, 1, 200, HARD, TORSO, null)),
            edgeWeak(armor("flak_pants", "Брюки Flak", 20, 1, 200, HARD, LEGS, null)),
            armor("nylon_helmet", "Нейлоновый шлем", 20, 0, 100, HARD, HEAD, null),
            armor("heavy_jacket", "Тяжёлая бронекуртка", 20, 2, 250, SOFT, TORSO_ARMS, null),
            armor("door_gunner_vest", "Жилет бортстрелка", 25, 3, 250, HARD, TORSO, null),
            armor("metalgear", "MetalGear", 25, 2, 600, HARD, WHOLE_BODY, null));

    private Cp2020Armors() {
    }

    /** Отметка √ в таблице с. 99 */
    private static CatalogItem edgeWeak(CatalogItem item) {
        Map<String, String> properties = new HashMap<>(item.properties());
        properties.put(Cp2020ItemTypes.EDGE_WEAK, "1");
        return new CatalogItem(item.key(), item.group(), item.name(), item.typeCode(), item.cost(), item.description(), properties, item.packSize(), item.individual());
    }

    private static CatalogItem armor(String key, String name, int sp, int encumbrance, int cost, String hardness,
                                     List<Cp2020Location> zones, String note) {
        Map<String, String> properties = new HashMap<>();
        properties.put(Cp2020ItemTypes.ARMOR_SP, String.valueOf(sp));
        properties.put(Cp2020ItemTypes.ENCUMBRANCE, String.valueOf(encumbrance));
        properties.put(Cp2020ItemTypes.HARDNESS, hardness);
        zones.forEach(zone -> properties.put(Cp2020ItemTypes.zoneKey(zone), "1"));
        return new CatalogItem("armor_" + key, GROUP, name, Cp2020ItemTypes.ARMOR, cost, note, properties, 1, true);
    }
}
