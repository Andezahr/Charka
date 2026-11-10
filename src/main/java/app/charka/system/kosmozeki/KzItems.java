package app.charka.system.kosmozeki;

import app.charka.item.CatalogItem;
import app.charka.item.ItemField;
import app.charka.item.ItemField.Option;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Character;
import app.charka.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Типы предметов Космозэков (раздел 5 и 9 {@code RULES_KOSMOZEKI.md}): оружие, взрывчатка, броня, патроны, справочник товаров.
 * Цен нет: единых цен в мире нет, справочник — только чтобы не набирать названия.
 */
public final class KzItems {

    private KzItems() {
    }

    public static final String RANGED = "KZ_RANGED";
    public static final String MELEE = "KZ_MELEE";
    public static final String EXPLOSIVE = "KZ_EXPLOSIVE";
    public static final String ARMOR = "KZ_ARMOR";
    public static final String AMMO = "KZ_AMMO";
    public static final String MEDICINE = "KZ_MEDICINE";

    /** Урон одной пули или удара */
    public static final String DMG = "dmg";
    /** Стихийный урон: за столом пока не встречался, хранится для мастера */
    public static final String EDMG = "edmg";
    /** Патронов в магазине */
    public static final String CAPACITY = "ammo";
    public static final String ROF = "rof";
    public static final String DIF = "dif";
    /** Сколько сейчас в магазине */
    public static final String LOADED = "loaded";
    /** Какие патроны подходят: название, по нему ищется запас */
    public static final String CALIBER = "caliber";
    /** Атак ближнего оружия за ход */
    public static final String ATTACKS = "attacks";
    /** Броня против этого оружия вдвое меньше */
    public static final String ARMOR_HALF = "armorHalf";
    public static final String NOTES = "notes";
    /** Какие зоны закрывает броня */
    public static final String ZONE = "zone";
    public static final String ARMOR_VALUE = "armor";
    public static final String WORN = "worn";
    /** Лечение «Жидкой кожи»: в одну зону и во все */
    public static final String HEAL_ONE = "healOne";
    public static final String HEAL_ALL = "healAll";

    static final String ZONE_ARMS = "arms";
    static final String ZONE_LEGS = "legs";
    static final String ZONE_ALL = "all";

    static final List<Option> ARMOR_ZONES = List.of(
            new Option(KzBody.HEAD, "Голова"),
            new Option(KzBody.BODY, "Тело"),
            new Option(ZONE_ARMS, "Руки"),
            new Option(ZONE_LEGS, "Ноги"),
            new Option(KzBody.RIGHT_ARM, "Правая рука"),
            new Option(KzBody.LEFT_ARM, "Левая рука"),
            new Option(KzBody.RIGHT_LEG, "Правая нога"),
            new Option(KzBody.LEFT_LEG, "Левая нога"),
            new Option(ZONE_ALL, "Всё тело"));

    /** KF-02: патроны выбираются из списка — «Другой…» оставляет своё название; у оружия и запаса одно и то же слово */
    static final List<String> CALIBERS = List.of("Пистолетные", "Винтовочные", "Дробь", "Снайперские");
    private static final List<Option> CALIBER_OPTIONS = CALIBERS.stream().map(caliber -> new Option(caliber, caliber)).toList();

    private static final ItemField DMG_FIELD = ItemField.number(DMG, "Урон (DMG)", "", " урона", 0, 1000);
    private static final ItemField DIF_FIELD = ItemField.number(DIF, "Сложность попадания (DIF)", "DIF ", "", 0, 200);
    private static final ItemField NOTES_FIELD = ItemField.text(NOTES, "Свойства").asAdvanced();

    public static final ItemTypeDefinition RANGED_TYPE = new ItemTypeDefinition(RANGED, "Дальнобойное оружие", List.of(
            DMG_FIELD,
            ItemField.number(ROF, "Скорострельность (ROF)", "ROF ", "", 1, 200),
            DIF_FIELD,
            ItemField.number(CAPACITY, "Патронов в магазине (AMMO)", "", " в магазине", 1, 1000),
            ItemField.suggested(CALIBER, "Патроны", false, "", CALIBER_OPTIONS),
            ItemField.number(LOADED, "Сейчас в магазине", "", "", 0, 1000).asAdvanced(),
            ItemField.number(EDMG, "Стихийный урон (EDMG)", "+", " стихии", 0, 1000).asAdvanced(),
            NOTES_FIELD), KzItems::checkRanged, List.of(LOADED)).inSection("Оружие");

    public static final ItemTypeDefinition MELEE_TYPE = new ItemTypeDefinition(MELEE, "Оружие ближнего боя", List.of(
            DMG_FIELD,
            ItemField.number(ATTACKS, "Атак за ход", "×", " за ход", 1, 20),
            DIF_FIELD,
            ItemField.flag(ARMOR_HALF, "Броня против него вдвое меньше", "броня ½"),
            NOTES_FIELD)).inSection("Оружие");

    public static final ItemTypeDefinition EXPLOSIVE_TYPE = new ItemTypeDefinition(EXPLOSIVE, "Взрывчатка", List.of(
            DMG_FIELD, DIF_FIELD, NOTES_FIELD)).inSection("Оружие").withStacking();

    public static final ItemTypeDefinition ARMOR_TYPE = new ItemTypeDefinition(ARMOR, "Броня", List.of(
            ItemField.choice(ZONE, "Зона", true, ARMOR_ZONES.toArray(Option[]::new)),
            ItemField.number(ARMOR_VALUE, "Броня", "броня ", "", 0, 500),
            ItemField.flag(WORN, "Надета", "надета").asAdvanced()), values -> {
    }, List.of(WORN)).inSection("Броня");

    public static final ItemTypeDefinition AMMO_TYPE = new ItemTypeDefinition(AMMO, "Патроны", List.of(
            ItemField.suggested(CALIBER, "Каких", true, "Патроны: ", CALIBER_OPTIONS))).inSection("Патроны").withStacking();

    public static final ItemTypeDefinition MEDICINE_TYPE = new ItemTypeDefinition(MEDICINE, "Лечилка", List.of(
            ItemField.number(HEAL_ONE, "ХП в одну зону", "+", " в зону", 0, 1000),
            ItemField.number(HEAL_ALL, "ХП во все зоны", "+", " во все", 0, 1000))).inSection("Вещи").withStacking();

    public static final List<ItemTypeDefinition> ALL = List.of(ItemTypeDefinition.GENERIC, RANGED_TYPE, MELEE_TYPE, EXPLOSIVE_TYPE,
            ARMOR_TYPE, AMMO_TYPE, MEDICINE_TYPE);

    private static void checkRanged(Map<String, String> values) {
        if (values.containsKey(LOADED) && values.containsKey(CAPACITY)
                && Integer.parseInt(values.get(LOADED)) > Integer.parseInt(values.get(CAPACITY))) {
            throw new IllegalArgumentException("В магазине не может быть больше " + values.get(CAPACITY) + " патронов");
        }
    }

    // ---------- чтение свойств ----------

    public static boolean isRanged(Item item) {
        return RANGED.equals(item.getType());
    }

    public static boolean isMelee(Item item) {
        return MELEE.equals(item.getType());
    }

    public static boolean isExplosive(Item item) {
        return EXPLOSIVE.equals(item.getType());
    }

    public static boolean isArmor(Item item) {
        return ARMOR.equals(item.getType());
    }

    public static boolean isAmmo(Item item) {
        return AMMO.equals(item.getType());
    }

    public static boolean isMedicine(Item item) {
        return MEDICINE.equals(item.getType());
    }

    public static boolean isWeapon(Item item) {
        return isRanged(item) || isMelee(item) || isExplosive(item);
    }

    static int number(Item item, String key, int defaultValue) {
        String value = item.getProperties().get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Патронов в магазине; не указано — магазин полный */
    public static int loaded(Item item) {
        return number(item, LOADED, number(item, CAPACITY, 0));
    }

    public static boolean isWorn(Item item) {
        return item.getProperties().containsKey(WORN);
    }

    /** Закрывает ли броня зону с этим кодом */
    public static boolean covers(Item armor, String zoneCode) {
        String zone = armor.getProperties().get(ZONE);
        if (zone == null) {
            return false;
        }
        return zone.equals(zoneCode) || ZONE_ALL.equals(zone)
                || (ZONE_ARMS.equals(zone) && (KzBody.RIGHT_ARM.equals(zoneCode) || KzBody.LEFT_ARM.equals(zoneCode)))
                || (ZONE_LEGS.equals(zone) && (KzBody.RIGHT_LEG.equals(zoneCode) || KzBody.LEFT_LEG.equals(zoneCode)));
    }

    /** Надетая броня персонажа */
    public static Stream<Item> wornArmor(Character character) {
        if (character == null || character.getInventories() == null) {
            return Stream.empty();
        }
        return character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(item -> isArmor(item) && isWorn(item));
    }

    /** Подходящий к оружию запас патронов: то же название патронов; у оружия без названия — любые */
    public static List<Item> ammoFor(Item weapon) {
        Character owner = weapon.getInventory() == null ? null : weapon.getInventory().getCharacter();
        if (owner == null) {
            return List.of();
        }
        String caliber = weapon.getProperties().get(CALIBER);
        return owner.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(KzItems::isAmmo)
                .filter(item -> item.getQuantity() != null && item.getQuantity() > 0)
                .filter(item -> caliber == null || caliber.isBlank() || caliber.strip().equalsIgnoreCase(item.getProperties().getOrDefault(CALIBER, "").strip()))
                .toList();
    }

    // ---------- справочник ----------

    private static CatalogItem entry(String key, String group, String name, String type, String description, Map<String, String> properties, int pack) {
        return new CatalogItem(key, group, name, type, 0, description, properties, pack);
    }

    private static CatalogItem gun(String key, String name, int dmg, int rof, int dif, int ammo, String caliber, String description) {
        return entry(key, "Оружие", name, RANGED, description, Map.of(DMG, String.valueOf(dmg), ROF, String.valueOf(rof),
                DIF, String.valueOf(dif), CAPACITY, String.valueOf(ammo), LOADED, String.valueOf(ammo), CALIBER, caliber), 1);
    }

    private static CatalogItem goods(String key, String name, String description) {
        return entry(key, "Ходовые товары", name, ItemTypeDefinition.GENERIC_CODE, description, Map.of(), 1);
    }

    /**
     * Пистолет и винтовка — примеры из документа системы; остальное оружие и броня — HOMEBREW-заготовки: числа правит мастер.
     * UX-27: пометки «HOMEBREW» и «пример из правил» — только здесь, в коде: в описании предмета их видят игроки.
     */
    public static final List<CatalogItem> CATALOG;

    static {
        List<CatalogItem> catalog = new ArrayList<>();
        catalog.add(gun("kz-pistol", "Стандартный пистолет", 20, 2, 30, 7, "Пистолетные", null));
        catalog.add(gun("kz-rifle", "Автоматическая винтовка", 20, 30, 50, 30, "Винтовочные", null));
        catalog.add(gun("kz-shotgun", "Дробовик", 40, 1, 30, 6, "Дробь", null));
        catalog.add(gun("kz-sniper", "Снайперская винтовка", 60, 1, 40, 5, "Снайперские", null));
        catalog.add(entry("kz-knife", "Оружие", "Нож", MELEE, null,
                Map.of(DMG, "15", ATTACKS, "2", DIF, "30", ARMOR_HALF, "1"), 1));
        catalog.add(entry("kz-club", "Оружие", "Дубинка", MELEE, null,
                Map.of(DMG, "20", ATTACKS, "1", DIF, "25", ARMOR_HALF, "1"), 1));
        catalog.add(entry("kz-grenade", "Оружие", "Граната", EXPLOSIVE, "Зоны, в которые пришёлся взрыв, выбирает мастер",
                Map.of(DMG, "30", DIF, "30"), 1));
        for (String caliber : CALIBERS) {
            catalog.add(entry("kz-ammo-" + caliber, "Патроны", "Патроны: " + caliber.toLowerCase(), AMMO, null,
                    Map.of(CALIBER, caliber), 10));
        }
        catalog.add(entry("kz-helmet", "Броня", "Шлем", ARMOR, null, Map.of(ZONE, KzBody.HEAD, ARMOR_VALUE, "20"), 1));
        catalog.add(entry("kz-vest", "Броня", "Бронежилет", ARMOR, null, Map.of(ZONE, KzBody.BODY, ARMOR_VALUE, "20"), 1));
        catalog.add(entry("kz-skin", "Ходовые товары", "«Жидкая кожа» PISOS", MEDICINE,
                "Быстрозастывающий биогерметик: +50 ХП в одну зону или +30 во все", Map.of(HEAL_ONE, "50", HEAL_ALL, "30"), 1));
        catalog.add(goods("kz-link", "Линк", "Рация, система радиосвязи LINK"));
        catalog.add(goods("kz-funbox", "Фанбокс", "Радио или медиаустройство Fun for Fan"));
        catalog.add(goods("kz-cassette", "Голокассета", "Носитель видео и аудио"));
        catalog.add(goods("kz-dragon", "«Золотой дракон»", "Элитные сигареты «Чёрного дракона»"));
        catalog.add(goods("kz-smokes", "«Дымки»", "Дешёвые самокрутки, поштучно"));
        catalog.add(goods("kz-churger", "«Чургеры»", "Лучший кофе во вселенной, баночка"));
        catalog.add(goods("kz-shake", "«Болтушка»", "Жидкая каша-заменитель в тетрапаке"));
        catalog.add(goods("kz-catdog", "«Котопёс»", "Консервы из двух синт-белков"));
        catalog.add(goods("kz-momentos", "«Моментос»", "Очень клейкая жвачка"));
        catalog.add(goods("kz-adrenalit", "Adrenalit Energy", "Энергетик с десятикратным кофеином"));
        catalog.add(goods("kz-gubnyak", "Губняк", "Лёгкий наркотик для настроения"));
        CATALOG = List.copyOf(catalog);
    }
}
