package app.charka.system.cp2020;

import app.charka.item.ItemField;
import app.charka.item.ItemField.Option;
import app.charka.item.ItemTypeDefinition;

import java.util.List;
import java.util.Map;

/**
 * Типы предметов CP2020. Поля оружия — код оружия книги (с. 60; дробовик в кодах и таблицах — SHT): тип, точность, скрытность, доступность,
 * урон и патрон, магазин, скорострельность, надёжность; дальность — из таблицы оружия (с. 62).
 * Заряжено и бронебойность — состояние магазина: бронебойность задаёт патрон (с. 64: «Armor piercing = 3x cost»; FNFF: «AP rounds»).
 */
public final class Cp2020ItemTypes {

    public static final String WEAPON = "CP2020_WEAPON";
    public static final String AMMO = "CP2020_AMMO";

    public static final String CALIBER = "caliber";
    public static final String SHOTS = "shots";
    public static final String LOADED = "ammo";
    public static final String RATE_OF_FIRE = "rof";
    public static final String ARMOR_PIERCING = "armorPiercing";

    /**
     * Урон — текст, а не кубики: в книге у части оружия не только они («1-5D6», «Drugs», «3D6/2D6»).
     */
    public static final ItemTypeDefinition WEAPON_TYPE = new ItemTypeDefinition(WEAPON, "Оружие", List.of(
            ItemField.choice("category", "Тип", true,
                    new Option("P", "Пистолет"),
                    new Option("SMG", "Пистолет-пулемёт"),
                    new Option("SHT", "Дробовик"),
                    new Option("RIF", "Винтовка"),
                    new Option("HVY", "Тяжёлое"),
                    new Option("MELEE", "Холодное"),
                    new Option("EX", "Экзотическое")),
            ItemField.number("accuracy", "Точность (WA)", "WA ", "", -3, 3),
            ItemField.choice("concealability", "Скрытность", false,
                    new Option("P", "Карман, штанина или рукав"),
                    new Option("J", "Куртка, плащ или кобура"),
                    new Option("L", "Длинное пальто"),
                    new Option("N", "Не спрятать")),
            ItemField.choice("availability", "Доступность", false,
                    new Option("E", "Отличная: почти везде"),
                    new Option("C", "Обычная: магазины, улица"),
                    new Option("P", "Плохая: чёрный рынок, армия"),
                    new Option("R", "Редкая: штучное, незаконное")),
            ItemField.text("damage", "Урон"),
            ItemField.text(CALIBER, "Патрон"),
            ItemField.number(SHOTS, "Магазин", "магазин ", "", 0, null),
            ItemField.number(LOADED, "Заряжено", "заряжено ", "", 0, null),
            ItemField.number(RATE_OF_FIRE, "Темп (ROF)", "ROF ", "", 0, null),
            ItemField.choice("reliability", "Надёжность", false,
                    new Option("VR", "Очень надёжное"),
                    new Option("ST", "Обычное"),
                    new Option("UR", "Ненадёжное")),
            ItemField.number("range", "Дальность, м", "", " м", 0, null),
            ItemField.flag(ARMOR_PIERCING, "Заряжены бронебойные", "AP")),
            Cp2020ItemTypes::checkMagazine);

    /** Количество патронов — количество предмета */
    public static final ItemTypeDefinition AMMO_TYPE = new ItemTypeDefinition(AMMO, "Боеприпасы", List.of(
            ItemField.requiredText(CALIBER, "Патрон"),
            ItemField.flag(ARMOR_PIERCING, "Бронебойные", "AP")));

    public static final List<ItemTypeDefinition> ALL = List.of(ItemTypeDefinition.GENERIC, WEAPON_TYPE, AMMO_TYPE);

    private Cp2020ItemTypes() {
    }

    private static void checkMagazine(Map<String, String> values) {
        int loaded = Integer.parseInt(values.getOrDefault(LOADED, "0"));
        int shots = Integer.parseInt(values.getOrDefault(SHOTS, "0"));
        if (loaded > shots) {
            throw new IllegalArgumentException("Заряжено больше, чем вмещает магазин: " + loaded + " из " + shots);
        }
        if (loaded == 0 && values.containsKey(ARMOR_PIERCING)) {
            throw new IllegalArgumentException("Бронебойные патроны можно отметить только в заряженном оружии");
        }
    }
}
