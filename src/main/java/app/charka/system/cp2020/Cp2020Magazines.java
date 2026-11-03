package app.charka.system.cp2020;

import app.charka.item.CatalogItem;
import app.charka.model.Item;
import app.charka.system.cp2020.Cp2020MagazineRules.Rate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Справочник магазинов: стандартный и расширенные на каждое сочетание патрона и вместимости у оружия из {@link Cp2020Weapons}.
 * Цены и размеры — {@link Cp2020MagazineRules}.
 */
public final class Cp2020Magazines {

    private static final String GROUP = "Магазины";
    private static final String OLD_GROUP = "Магазины для старого оружия";
    private static final String HEAVY_GROUP = "Магазины для тяжёлого оружия";

    private static final String STANDARD_NOTE = "Пустой. Хоумбрю: в книгах нет цены стандартного магазина, взята ставка расширенного";
    private static final String EXTENDED_NOTE = "Пустой. Цена по Solo of Fortune II";
    private static final String OVERSIZED_NOTE = EXTENDED_NOTE + ". Больше двух стандартных: штрафы к надёжности, скрытности и инициативе";

    public static final List<CatalogItem> CATALOG = build();

    private Cp2020Magazines() {
    }

    private static List<CatalogItem> build() {
        Map<String, CatalogItem> rows = new LinkedHashMap<>();
        for (CatalogItem weapon : Cp2020Weapons.CATALOG) {
            Map<String, String> properties = weapon.properties();
            String caliber = properties.get(Cp2020ItemTypes.CALIBER);
            String shots = properties.get(Cp2020ItemTypes.SHOTS);
            if (caliber != null && shots != null && !properties.containsKey(Cp2020ItemTypes.DIRECT_LOAD)) {
                int standard = Integer.parseInt(shots);
                Rate rate = rate(weapon);
                add(rows, caliber, standard, standard, rate);
                Cp2020MagazineRules.CATALOG_EXTENSIONS.forEach(times -> add(rows, caliber, standard, standard * times, rate));
            }
        }
        return List.copyOf(rows.values());
    }

    private static Rate rate(CatalogItem weapon) {
        if ("HVY".equals(weapon.properties().get("category"))) {
            return Rate.HEAVY;
        }
        return Cp2020Weapons.OLD.equals(weapon.group()) ? Rate.CASED : Rate.CASELESS;
    }

    private static void add(Map<String, CatalogItem> rows, String caliber, int standard, int capacity, Rate rate) {
        String key = "mag_" + caliber.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_", "")
                + "_" + capacity + "_" + rate.name().toLowerCase(Locale.ROOT);
        String note = capacity == standard ? STANDARD_NOTE
                : Cp2020MagazineRules.isOversized(standard, capacity) ? OVERSIZED_NOTE : EXTENDED_NOTE;
        rows.putIfAbsent(key, new CatalogItem(key, group(rate), name(caliber, capacity), Cp2020ItemTypes.MAGAZINE,
                Cp2020MagazineRules.price(capacity, rate), note,
                Map.of(Cp2020ItemTypes.CALIBER, caliber, Cp2020ItemTypes.SHOTS, String.valueOf(capacity), Cp2020ItemTypes.LOADED, "0"), 1, true));
    }

    private static String group(Rate rate) {
        return switch (rate) {
            case CASELESS -> GROUP;
            case CASED -> OLD_GROUP;
            case HEAVY -> HEAVY_GROUP;
        };
    }

    private static String name(String caliber, int capacity) {
        return "Магазин " + caliber + " на " + capacity;
    }

    /**
     * Пустой стандартный магазин к оружию, та же цена, что в справочнике. Ставка старого оружия определяется по названию из справочника.
     */
    static Item standardFor(String weaponName, String caliber, int capacity, String category) {
        boolean old = Cp2020Weapons.CATALOG.stream()
                .anyMatch(entry -> entry.name().equals(weaponName) && Cp2020Weapons.OLD.equals(entry.group()));
        Rate rate = "HVY".equals(category) ? Rate.HEAVY : old ? Rate.CASED : Rate.CASELESS;
        Item magazine = newMagazine(caliber, capacity, 0, false);
        magazine.setCost(Cp2020MagazineRules.price(capacity, rate));
        magazine.setDescription(STANDARD_NOTE);
        return magazine;
    }

    /**
     * Магазин, вынутый из оружия.
     */
    static Item newMagazine(String caliber, int capacity, int loaded, boolean armorPiercing, boolean slug) {
        Item magazine = newMagazine(caliber, capacity, loaded, armorPiercing);
        if (slug && loaded > 0) {
            magazine.getProperties().put(Cp2020ItemTypes.SLUG, "1");
        }
        return magazine;
    }

    static Item newMagazine(String caliber, int capacity, int loaded, boolean armorPiercing) {
        Item magazine = new Item();
        magazine.setName(name(caliber, capacity));
        magazine.setQuantity(1);
        magazine.setCost(0);
        magazine.setType(Cp2020ItemTypes.MAGAZINE);
        magazine.getProperties().put(Cp2020ItemTypes.CALIBER, caliber);
        magazine.getProperties().put(Cp2020ItemTypes.SHOTS, String.valueOf(capacity));
        magazine.getProperties().put(Cp2020ItemTypes.LOADED, String.valueOf(loaded));
        if (armorPiercing && loaded > 0) {
            magazine.getProperties().put(Cp2020ItemTypes.ARMOR_PIERCING, "1");
        }
        return magazine;
    }
}
