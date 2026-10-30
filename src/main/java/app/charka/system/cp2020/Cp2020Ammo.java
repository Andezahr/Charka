package app.charka.system.cp2020;

import app.charka.item.CatalogItem;
import app.charka.item.ItemField;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Справочник боеприпасов — таблица «Reloads» книги (с. 64). Книга делит патроны по классу оружия, а перезарядка идёт по патрону,
 * поэтому у каждого класса строка на каждый патрон его оружия из {@link Cp2020Weapons}.
 * Бронебойные — ×3 (примечание к таблице); только у пуль, у которых FNFF описывает AP. Цена — за коробку.
 */
public final class Cp2020Ammo {

    /** HOMEBREW: у этого оружия патрон в книге не напечатан; названия наши, чтобы связать его со строкой таблицы */
    public static final String ARROWS = "Стрелы";
    public static final String BOLTS = "Болты";
    public static final String PELLETS = "Пульки";
    public static final String NEEDLES = "Иглы";
    public static final String NAPALM = "Напалм";
    public static final String MICRO_MISSILES = "Микроракеты";

    private static final String GROUP = "Боеприпасы";
    private static final String OTHER_GROUP = "Боеприпасы: другие";
    private static final String BRASS_GROUP = "Боеприпасы: латунная гильза для старого оружия";

    private static final int AP_MULTIPLIER = 3;
    private static final int BRASS_MULTIPLIER = 2;

    private static final String BOX_NOTE = "Цена за коробку";
    /** Книга: «Brass Cased loads for old guns = 2x cost». HOMEBREW: какой строке таблицы соответствует патрон старого оружия, в книге нет, класс выбран по урону */
    private static final String BRASS_NOTE = BOX_NOTE + ". Латунная гильза: ×2 к цене. Класс выбран по урону оружия, в книге соответствия нет";

    public static final List<CatalogItem> CATALOG = build();

    /** Все патроны справочника, для выбора в форме предмета */
    public static final List<ItemField.Option> CALIBERS = CATALOG.stream()
            .map(entry -> entry.properties().get(Cp2020ItemTypes.CALIBER))
            .distinct()
            .map(caliber -> new ItemField.Option(caliber, caliber))
            .toList();

    private Cp2020Ammo() {
    }

    private static List<CatalogItem> build() {
        List<CatalogItem> rows = new ArrayList<>();
        bullets(rows, "лёгкий пистолет, лёгкий ПП", 100, 15, "5mm", "6mm", "9mm", "10mm");
        bullets(rows, "средний пистолет, ПП", 50, 15, "9mm", "10mm");
        bullets(rows, "тяжёлый пистолет, тяжёлый ПП", 50, 18, "11mm", "12mm");
        bullets(rows, "очень тяжёлый пистолет", 50, 20, "12mm");
        bullets(rows, "штурмовая винтовка", 100, 40, "5.56", "7.62");
        bullets(rows, "дробовик", 12, 15, "00");

        // Подкалиберный снаряд Barrett-Arasaka (с. 63): бронебойный всегда, отдельной цены ×3 у него нет
        rows.add(row("ammo_20mm", OTHER_GROUP, "20mm Cannon, снаряд", "20/9mm", true, 1, 25, null));
        rows.add(other("arrows", "Стрелы (12)", ARROWS, 12, 24, BOX_NOTE));
        rows.add(other("bolts", "Болты для арбалета (12)", BOLTS, 12, 30, BOX_NOTE));
        rows.add(other("pellets", "Пульки для пневматики (100)", PELLETS, 100, 6, BOX_NOTE + ". С наркотиком или кислотой — ×5 к цене"));
        rows.add(other("needles", "Иглы (50)", NEEDLES, 50, 25, BOX_NOTE));
        // HOMEBREW: в книге одна заправка без числа выстрелов; 10 — магазин K-A F-253
        rows.add(other("napalm", "Заправка огнемёта", NAPALM, 10, 50, "Цена за заправку. В книге без числа выстрелов, взят магазин F-253"));
        rows.add(other("micro_missiles", "Микроракеты (4)", MICRO_MISSILES, 4, 100, "Цена за 4"));

        brass(rows, "лёгкий пистолет", 100, 15, ".25", ".38");
        brass(rows, "средний пистолет", 50, 15, ".357", ".45");
        brass(rows, "очень тяжёлый пистолет", 50, 20, ".44");
        brass(rows, "штурмовая винтовка", 100, 40, "7.56", "7.56S", "30-06");
        return List.copyOf(rows);
    }

    private static void bullets(List<CatalogItem> rows, String weaponClass, int boxSize, int cost, String... calibers) {
        for (String caliber : calibers) {
            String name = caliber + " (" + weaponClass + "), коробка " + boxSize;
            String key = key(caliber, cost, boxSize);
            rows.add(row(key, GROUP, name, caliber, false, boxSize, cost, BOX_NOTE));
            rows.add(row(key + "_ap", GROUP, name + " AP", caliber, true, boxSize, cost * AP_MULTIPLIER, BOX_NOTE));
        }
    }

    private static void brass(List<CatalogItem> rows, String weaponClass, int boxSize, int cost, String... calibers) {
        for (String caliber : calibers) {
            rows.add(row("brass_" + key(caliber, cost, boxSize), BRASS_GROUP, caliber + " (как " + weaponClass + "), коробка " + boxSize,
                    caliber, false, boxSize, cost * BRASS_MULTIPLIER, BRASS_NOTE));
        }
    }

    private static CatalogItem other(String key, String name, String caliber, int pack, int cost, String note) {
        return row("ammo_" + key, OTHER_GROUP, name, caliber, false, pack, cost, note);
    }

    private static CatalogItem row(String key, String group, String name, String caliber, boolean armorPiercing,
                                   int pack, int cost, String note) {
        Map<String, String> properties = armorPiercing
                ? Map.of(Cp2020ItemTypes.CALIBER, caliber, Cp2020ItemTypes.ARMOR_PIERCING, "1")
                : Map.of(Cp2020ItemTypes.CALIBER, caliber);
        return new CatalogItem(key, group, name, Cp2020ItemTypes.AMMO, cost, note, properties, pack);
    }

    private static String key(String caliber, int cost, int boxSize) {
        return "ammo_" + caliber.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_", "") + "_" + boxSize + "_" + cost;
    }
}
