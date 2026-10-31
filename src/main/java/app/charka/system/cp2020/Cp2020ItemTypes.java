package app.charka.system.cp2020;

import app.charka.item.ItemField;
import app.charka.item.ItemField.Option;
import app.charka.item.ItemTypeDefinition;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Типы предметов CP2020. Поля оружия — код оружия книги (с. 60; дробовик в кодах и таблицах — SHT): тип, точность, скрытность, доступность,
 * урон и патрон, магазин, скорострельность, надёжность; дальность — из таблицы оружия (с. 62).
 * Заряжено и бронебойность — состояние магазина: бронебойность задаёт патрон (с. 64: «Armor piercing = 3x cost»; FNFF: «AP rounds»).
 * «Заряжено» у оружия не указано — магазина в нём нет. HOMEBREW: какое оружие заряжается без магазина, книга прямо не делит.
 */
public final class Cp2020ItemTypes {

    public static final String WEAPON = "CP2020_WEAPON";
    public static final String AMMO = "CP2020_AMMO";
    public static final String MAGAZINE = "CP2020_MAGAZINE";

    public static final String CALIBER = "caliber";
    public static final String SHOTS = "shots";
    public static final String LOADED = "ammo";
    public static final String RATE_OF_FIRE = "rof";
    public static final String ARMOR_PIERCING = "armorPiercing";
    public static final String DIRECT_LOAD = "directLoad";
    /** Размер вставленного магазина; указан, только если он не стандартный */
    public static final String INSERTED = "insertedMagazine";
    /** Сколько SP снимает попадание, если больше обычного 1 (Barrett-Arasaka, с. 63) */
    public static final String ARMOR_WEAR = "armorWear";
    /** Метается; количество предмета — сколько осталось */
    public static final String THROWN = "thrown";
    /** Подрывается на месте, без броска на попадание */
    public static final String PLANTED = "planted";
    /** Зона поражения, м (с. 99); есть — урон по площади */
    public static final String AREA = "area";
    /** Взрывчатка (с. 111): зона умножается на число единиц, урон по всему телу. Единица — количество предмета */
    public static final String EXPLOSIVE = "explosive";
    /** Заряжены пули к дробовику (с. 109): урон по калибру, одна цель */
    public static final String SLUG = "slug";
    /** Калибр дробовика: 10, 12 или 20 */
    public static final String GAUGE = "gauge";
    /** Лезвие холодного оружия: {@link Cp2020DamageType#BLADE} или MONO */
    public static final String EDGE = "edge";
    /** Броня с отметкой √ (с. 99): клинок считает её SP за половину */
    public static final String EDGE_WEAK = "edgeWeak";
    /** Гранатомёт: заряд — граната из инвентаря, её урон, зона и эффект переходят на оружие */
    public static final String LAUNCHER = "launcher";
    /** Какая граната заряжена; состояние, не поле формы */
    public static final String CHARGE = "charge";
    /** Единица взрывчатки: кг, шашка, литр */
    public static final String UNIT = "unit";
    /** Что заряд делает сверх урона: {@link Cp2020Effects} */
    public static final String EFFECT = "effect";
    /** Оружие заклинило; состояние, не поле формы */
    public static final String JAMMED = "jammed";

    public static final String ARMOR = "CP2020_ARMOR";
    public static final String ARMOR_SP = "sp";
    public static final String ENCUMBRANCE = "ev";
    public static final String HARDNESS = "hardness";
    public static final String SOFT = "SOFT";
    public static final String HARD = "HARD";
    /** Порядок надевания; есть, только пока броня надета */
    public static final String WORN = "worn";
    private static final String ZONE_PREFIX = "zone_";
    private static final String WEAR_PREFIX = "wear_";
    /** Сюда попадают только магазины и патроны, к которым нет оружия: остальные показаны под ним */
    private static final String AMMO_SECTION = "Боеприпасы";

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
            ItemField.suggested(CALIBER, "Патрон", false, "", Cp2020Ammo.CALIBERS),
            ItemField.number(SHOTS, "Магазин", "магазин ", "", 0, null),
            ItemField.number(LOADED, "Заряжено", "заряжено ", "", 0, null).asAdvanced(),
            ItemField.number(INSERTED, "Вставлен магазин на", "вставлен на ", "", 1, null).asAdvanced(),
            ItemField.number(RATE_OF_FIRE, "Темп (ROF)", "ROF ", "", 0, null),
            ItemField.choice("reliability", "Надёжность", false,
                    new Option("VR", "Очень надёжное"),
                    new Option("ST", "Обычное"),
                    new Option("UR", "Ненадёжное")),
            ItemField.number("range", "Дальность, м", "", " м", 0, null),
            ItemField.flag(ARMOR_PIERCING, "Заряжены бронебойные", "AP").asAdvanced(),
            ItemField.flag(DIRECT_LOAD, "Заряжается без магазина", "без магазина").asAdvanced(),
            ItemField.number(ARMOR_WEAR, "SP брони за попадание", "снимает SP ", "", 2, 10).asAdvanced(),
            ItemField.flag(THROWN, "Метается", "").asAdvanced(),
            ItemField.flag(PLANTED, "Подрывается на месте", "").asAdvanced(),
            ItemField.number(AREA, "Зона поражения, м", "зона ", " м", 1, null).asAdvanced(),
            ItemField.flag(EXPLOSIVE, "Взрывчатка: зона на единицу, по всему телу", "").asAdvanced(),
            ItemField.flag(LAUNCHER, "Гранатомёт: заряд — граната", "").asAdvanced(),
            ItemField.flag(SLUG, "Заряжены пули", "пули").asAdvanced(),
            ItemField.choice(GAUGE, "Калибр дробовика", false, new Option("10", "10"), new Option("12", "12"), new Option("20", "20")).asAdvanced(),
            ItemField.choice(EDGE, "Лезвие", false, new Option("BLADE", "Клинок"), new Option("MONO", "Моно-лезвие")).asAdvanced(),
            ItemField.text(UNIT, "Единица взрывчатки").asAdvanced(),
            ItemField.choice(EFFECT, "Особый эффект", false, Cp2020Effects.OPTIONS.toArray(new Option[0])).asAdvanced()),
            Cp2020ItemTypes::checkWeapon,
            List.of(JAMMED, CHARGE)).inSection("Оружие");

    /** Каждая строка — один магазин со своим зарядом */
    public static final ItemTypeDefinition MAGAZINE_TYPE = new ItemTypeDefinition(MAGAZINE, "Магазин", List.of(
            ItemField.suggested(CALIBER, "Патрон", true, "Магазин ", Cp2020Ammo.CALIBERS),
            ItemField.number(SHOTS, "Вместимость", "на ", "", 1, null),
            ItemField.number(LOADED, "Заряжено", "заряжено ", "", 0, null),
            ItemField.flag(ARMOR_PIERCING, "Заряжены бронебойные", "AP"),
            ItemField.flag(SLUG, "Заряжены пули", "пули")),
            Cp2020ItemTypes::checkMagazine).inSection(AMMO_SECTION);

    /** Количество патронов — количество предмета */
    public static final ItemTypeDefinition AMMO_TYPE = new ItemTypeDefinition(AMMO, "Боеприпасы", List.of(
            ItemField.suggested(CALIBER, "Патрон", true, "Патроны ", Cp2020Ammo.CALIBERS),
            ItemField.flag(ARMOR_PIERCING, "Бронебойные", "AP"),
            ItemField.flag(SLUG, "Пули к дробовику", "пули"))).inSection(AMMO_SECTION).withStacking();

    public static String zoneKey(Cp2020Location location) {
        return ZONE_PREFIX + location.name();
    }

    public static String wearKey(Cp2020Location location) {
        return WEAR_PREFIX + location.name();
    }

    /**
     * Броня (с. 67; жёсткая или мягкая — FNFF, с. 101). Надета ли и износ — состояние, а не поля формы: правка предмета их не сбрасывает.
     */
    public static final ItemTypeDefinition ARMOR_TYPE = new ItemTypeDefinition(ARMOR, "Броня",
            Stream.concat(
                    Stream.of(ItemField.number(ARMOR_SP, "SP", "SP ", "", 0, 60),
                            ItemField.number(ENCUMBRANCE, "Штраф EV", "EV ", "", 0, 10),
                            ItemField.choice(HARDNESS, "Жёсткость", true,
                                    new Option(SOFT, "Мягкая: ткань, кевлар"),
                                    new Option(HARD, "Жёсткая: металл, керамика, композит")),
                            ItemField.flag(EDGE_WEAK, "Клинок считает SP за половину", "√")),
                    Arrays.stream(Cp2020Location.values())
                            .map(location -> ItemField.flag(zoneKey(location), location.getLabel(), location.getShortLabel())))
                    .toList(),
            Cp2020ItemTypes::checkArmor,
            Stream.concat(Stream.of(WORN), Arrays.stream(Cp2020Location.values()).map(Cp2020ItemTypes::wearKey)).toList());

    public static final List<ItemTypeDefinition> ALL = List.of(WEAPON_TYPE, ARMOR_TYPE, ItemTypeDefinition.GENERIC, MAGAZINE_TYPE, AMMO_TYPE);

    private Cp2020ItemTypes() {
    }

    private static void checkArmor(Map<String, String> values) {
        if (!values.containsKey(ARMOR_SP)) {
            throw new IllegalArgumentException("Поле «SP» обязательно");
        }
        boolean coversAny = Arrays.stream(Cp2020Location.values()).anyMatch(location -> values.containsKey(zoneKey(location)));
        if (!coversAny) {
            throw new IllegalArgumentException("Отметьте хотя бы одну зону, которую закрывает броня");
        }
    }

    private static void checkWeapon(Map<String, String> values) {
        int standard = Integer.parseInt(values.getOrDefault(SHOTS, "0"));
        if (values.containsKey(INSERTED) && !Cp2020MagazineRules.fits(standard, Integer.parseInt(values.get(INSERTED)))) {
            throw new IllegalArgumentException("Магазин на " + values.get(INSERTED) + " не подходит: нужен от стандартного "
                    + standard + " до " + standard * Cp2020MagazineRules.MAX_EXTENSION);
        }
        checkLoaded(values, Integer.parseInt(values.getOrDefault(INSERTED, values.getOrDefault(SHOTS, "0"))));
    }

    private static void checkMagazine(Map<String, String> values) {
        if (!values.containsKey(SHOTS)) {
            throw new IllegalArgumentException("Поле «Вместимость» обязательно");
        }
        values.putIfAbsent(LOADED, "0");
        checkLoaded(values, Integer.parseInt(values.get(SHOTS)));
    }

    private static void checkLoaded(Map<String, String> values, int capacity) {
        int loaded = Integer.parseInt(values.getOrDefault(LOADED, "0"));
        if (loaded > capacity) {
            throw new IllegalArgumentException("Заряжено больше, чем вмещает магазин: " + loaded + " из " + capacity);
        }
        if (loaded == 0 && values.containsKey(ARMOR_PIERCING)) {
            throw new IllegalArgumentException("Бронебойные патроны можно отметить только в заряженном оружии");
        }
    }
}
