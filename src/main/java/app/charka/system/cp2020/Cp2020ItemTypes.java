package app.charka.system.cp2020;

import app.charka.item.ItemField;
import app.charka.item.ItemField.Option;
import app.charka.item.ItemTypeDefinition;

import java.util.List;

/**
 * Типы предметов CP2020. Поля оружия — код оружия книги (с. 60; дробовик в кодах и таблицах — SHT): тип, точность, скрытность, доступность,
 * урон и патрон, магазин, скорострельность, надёжность; дальность — из таблицы оружия (с. 62).
 */
public final class Cp2020ItemTypes {

    public static final String WEAPON = "CP2020_WEAPON";

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
            ItemField.text("caliber", "Патрон"),
            ItemField.number("shots", "Магазин", "магазин ", "", 0, null),
            ItemField.number("rof", "Темп (ROF)", "ROF ", "", 0, null),
            ItemField.choice("reliability", "Надёжность", false,
                    new Option("VR", "Очень надёжное"),
                    new Option("ST", "Обычное"),
                    new Option("UR", "Ненадёжное")),
            ItemField.number("range", "Дальность, м", "", " м", 0, null),
            ItemField.flag("armorPiercing", "Бронебойное", "AP")));

    public static final List<ItemTypeDefinition> ALL = List.of(ItemTypeDefinition.GENERIC, WEAPON_TYPE);

    private Cp2020ItemTypes() {
    }
}
