package app.charka.system.cp2020;

import app.charka.item.CatalogItem;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Справочник оружия книги (с. 61, 62, 64–66). Только характеристики; описания остаются в книге.
 * Порядок значений как в таблице: тип, WA, скрытность, доступность, урон, патрон, магазин, ROF, надёжность, дальность, цена.
 */
public final class Cp2020Weapons {

    private static final String LIGHT_PISTOLS = "Лёгкие автопистолеты";
    private static final String MEDIUM_PISTOLS = "Средние автопистолеты";
    private static final String HEAVY_PISTOLS = "Тяжёлые автопистолеты";
    private static final String VERY_HEAVY_PISTOLS = "Очень тяжёлые автопистолеты";
    private static final String LIGHT_SMG = "Лёгкие пистолеты-пулемёты";
    private static final String MEDIUM_SMG = "Средние пистолеты-пулемёты";
    private static final String HEAVY_SMG = "Тяжёлые пистолеты-пулемёты";
    private static final String RIFLES = "Штурмовые винтовки";
    private static final String SHOTGUNS = "Дробовики";
    private static final String HEAVY = "Тяжёлое оружие";
    private static final String EXOTIC = "Экзотическое оружие";
    private static final String MONO = "Холодное оружие: высокие технологии";
    private static final String MELEE = "Холодное оружие";

    static final String OLD = "Старое оружие XX века";

    private static final String THROWN = "Дальность: бросок";
    /** Урон гранатомёта без заряда */
    static final String BY_CHARGE = "По типу";
    private static final String GAS_NOTE = "Броня не защищает, фильтры защищают";
    /** Книга (с. 66): старое оружие стоит около половины нового такого же типа, окончательно решает мастер */
    private static final String OLD_NOTE = "Цена: около половины нового оружия того же типа, решает мастер. Дальность в книге не указана";

    public static final List<CatalogItem> CATALOG = List.of(
            gun(LIGHT_PISTOLS, "BudgetArms C-13", "P", -1, "P", "E", "1D6", "5mm", 8, 2, "ST", 50, 75),
            gun(LIGHT_PISTOLS, "Dai Lung Cybermag 15", "P", -1, "P", "C", "1D6+1", "6mm", 10, 2, "UR", 50, 50),
            gun(LIGHT_PISTOLS, "Federated Arms X-22", "P", 0, "P", "E", "1D6+1", "6mm", 10, 2, "ST", 50, 150),

            gun(MEDIUM_PISTOLS, "Militech Arms Avenger", "P", 0, "J", "E", "2D6+1", "9mm", 10, 2, "VR", 50, 250),
            gun(MEDIUM_PISTOLS, "Dai Lung Streetmaster", "P", 0, "J", "E", "2D6+3", "10mm", 12, 2, "UR", 50, 250),
            gun(MEDIUM_PISTOLS, "Federated Arms X-9mm", "P", 0, "J", "E", "2D6+1", "9mm", 12, 2, "ST", 50, 300),

            gun(HEAVY_PISTOLS, "BudgetArms Auto 3", "P", -1, "J", "E", "3D6", "11mm", 8, 2, "UR", 50, 350),
            gun(HEAVY_PISTOLS, "Sternmeyer Type 35", "P", 0, "J", "C", "3D6", "11mm", 8, 2, "VR", 50, 400),

            gun(VERY_HEAVY_PISTOLS, "Armalite 44", "P", 0, "J", "E", "4D6+1", "12mm", 8, 1, "ST", 50, 450),
            gun(VERY_HEAVY_PISTOLS, "Colt AMT Model 2000", "P", 0, "J", "C", "4D6+1", "12mm", 8, 1, "VR", 50, 500),

            gun(LIGHT_SMG, "Uzi Miniauto 9", "SMG", 1, "J", "E", "2D6+1", "9mm", 30, 35, "VR", 150, 475),
            gun(LIGHT_SMG, "H&K MP-2013", "SMG", 1, "J", "C", "2D6+3", "10mm", 35, 32, "ST", 150, 450),
            gun(LIGHT_SMG, "Fed. Arms Tech Assault II", "SMG", 1, "J", "C", "1D6+1", "6mm", 50, 25, "ST", 150, 400),

            gun(MEDIUM_SMG, "Arasaka Minami 10", "SMG", 0, "J", "E", "2D6+3", "10mm", 40, 20, "VR", 200, 500),
            gun(MEDIUM_SMG, "H&K MPK-9", "SMG", 1, "J", "C", "2D6+1", "9mm", 35, 25, "ST", 200, 520),

            gun(HEAVY_SMG, "Sternmeyer SMG 21", "SMG", -1, "L", "E", "3D6", "11mm", 30, 15, "VR", 200, 500),
            gun(HEAVY_SMG, "H&K MPK-11", "SMG", 0, "L", "C", "4D6+1", "12mm", 30, 20, "ST", 200, 700),
            gun(HEAVY_SMG, "Ingram MAC 14", "SMG", -2, "L", "E", "4D6+1", "12mm", 20, 10, "ST", 200, 650),

            gun(RIFLES, "Militech Ronin Light Assault", "RIF", 1, "N", "C", "5D6", "5.56", 35, 30, "VR", 400, 450),
            gun(RIFLES, "AKR-20 Medium Assault", "RIF", 0, "N", "C", "5D6", "5.56", 30, 30, "ST", 400, 500),
            gun(RIFLES, "FN-RAL Heavy Assault Rifle", "RIF", -1, "N", "C", "6D6+2", "7.62", 30, 30, "VR", 400, 600),
            gun(RIFLES, "Kalishnikov A-80 Hvy. Rifle", "RIF", -1, "N", "E", "6D6+2", "7.62", 35, 25, "ST", 400, 550),

            gun(RIFLES, "Sternmeyer CG-13", "RIF", 1, "N", "P", "5D6", "5.56", 90, 48, "VR", null, 700),
            gun(RIFLES, "Fabrica De Armes M-2012", "RIF", 2, "N", "P", "6D6+2", "7.62", 30, 4, "VR", null, 1400),

            withProperty(gun(SHOTGUNS, "Arasaka Rapid Assault 12", "SHT", -1, "N", "C", "4D6", "00", 20, 10, "ST", 50, 900), Cp2020ItemTypes.GAUGE, "12"),
            withProperty(gun(SHOTGUNS, "Sternmeyer Stakeout 10", "SHT", -2, "N", "R", "4D6", "00", 10, 2, "ST", 50, 450), Cp2020ItemTypes.GAUGE, "10"),

            note(armorWear(armorPiercing(gun(HEAVY, "Barrett-Arasaka Light 20mm", "HVY", 0, "N", "R", "4D10", "20/9mm", 10, 1, "VR", 450, 2000)), 2),
                    "Подкалиберный бронебойный снаряд: броня теряет 2 SP за попадание"),
            // зоны — таблица Area Effect (с. 99); заряжаются из запаса: заряд один
            directLoad(withProperty(gun(HEAVY, "Scorpion 16 Missile Launcher", "HVY", -1, "N", "R", "7D10", Cp2020Ammo.MISSILES, 1, 1, "VR", 1000, 3000),
                    Cp2020ItemTypes.AREA, "6")),
            note(directLoad(withProperty(gun(HEAVY, "Militech Arms RPG-A", "HVY", -2, "N", "R", "6D10", Cp2020Ammo.RPG_ROUNDS, 1, 1, "VR", 750, 1500),
                    Cp2020ItemTypes.AREA, "4")), "Обратный выброс: нельзя стрелять в тесноте"),
            // одноразовое (граната, взрывчатка, мина): магазина нет, расходуется количеством предмета
            grenade("Fragmentation", "7D6", null),
            withProperty(grenade("Incendiary", "4D6", "Горит 3 хода: 4D6 в каждый"), Cp2020ItemTypes.EFFECT, Cp2020Effects.INCENDIARY),
            withProperty(grenade("Stun", "Stun −5", "Спасбросок от оглушения с −5"), Cp2020ItemTypes.EFFECT, Cp2020Effects.STUN),
            withProperty(grenade("Dazzle", "Blind 4", "Слепота на 4 хода"), Cp2020ItemTypes.EFFECT, Cp2020Effects.DAZZLE),
            withProperty(grenade("Sonic", "Deaf 4", "Глухота на 4 хода"), Cp2020ItemTypes.EFFECT, Cp2020Effects.SONIC),
            gas("Hallucinogen", "−4 INT", "Галлюциноген: при спасброске −2 INT"),
            gas("Nausea", "−4 REF", "Тошнота: при спасброске −2 REF"),
            gas("Teargas", "−2 REF", "Слезоточивый: при спасброске −1 REF"),
            gas("Sleep", "Сон", "Снотворный: при спасброске сонливость и −2 ко всем статам"),
            deadlyGas("Biotoxin I", "4D6"),
            deadlyGas("Biotoxin II", "8D6"),
            deadlyGas("Nerve", "8D10"),
            // HOMEBREW: новое оружие продаётся заряженным, но заряд гранатомёта — отдельная граната, поэтому он пуст
            note(withProperty(withFlag(gun(HEAVY, "Grenade Launcher", "HVY", 0, null, "R", BY_CHARGE, null, 1, 1, "ST", 225, 150),
                    Cp2020ItemTypes.LAUNCHER), Cp2020ItemTypes.LOADED, "0"),
                    "Заряд — граната из инвентаря. Скрытность в книге: L/N. Крепится под ствол штурмовой винтовки или используется с рук; не подходит к Militech RPG-A"),
            // HOMEBREW: на с. 63 урон «8D10 per kg», на с. 111 от числа единиц зависит только зона; взята с. 111
            note(withFlag(withFlag(withFlag(withProperty(gun(HEAVY, "C-6 Plastic Explosive", "HVY", 0, "P", "P", "8D10", null, null, 1, "VR", null, 100),
                    Cp2020ItemTypes.AREA, "5"), Cp2020ItemTypes.THROWN), Cp2020ItemTypes.PLANTED), Cp2020ItemTypes.EXPLOSIVE),
                    "Цена за кг. Количество — кг"),
            note(withFlag(withProperty(gun(HEAVY, "Mine (all types)", "HVY", 0, "J", "P", "4D10", null, null, 1, "VR", null, 350),
                    Cp2020ItemTypes.AREA, "2"), Cp2020ItemTypes.PLANTED), "Магнитный датчик находит обычную мину 7 из 10"),
            // цена — «Mine (all types)» (с. 63); конус на листе не нарисовать, цели отмечает мастер
            note(withFlag(withProperty(gun(HEAVY, "Claymore Mine", "HVY", 0, "J", "P", "4D10", null, null, 1, "VR", null, 350),
                    Cp2020ItemTypes.AREA, "6"), Cp2020ItemTypes.PLANTED), "Спереди конус 6 × 75 м, сзади 6 × 6 м, в стыке круг 6 м"),
            // С. 111; цен нет ни в таблице, ни в тексте
            explosive("Plastique", "7D10", 4, "кг", true, "Цены в книге нет"),
            explosive("TNT", "4D10", 3, "шашек", true, "Цены в книге нет"),
            explosive("Molotov Cocktail", "2D10", 2, "л", false, "2 м на литр; обычная бутылка — 1 литр. Цены в книге нет"),
            // HOMEBREW: оружие без магазина (огнемёт, лук, арбалет, револьверы, Winchester) заряжается патронами прямо из запаса
            note(directLoad(withProperty(withProperty(gun(HEAVY, "K-A F-253 Flamethrower", "HVY", -2, "N", "R", "2D10", Cp2020Ammo.NAPALM, 10, 1, "ST", 50, 1500),
                    Cp2020ItemTypes.AREA, "2"), Cp2020ItemTypes.EFFECT, Cp2020Effects.FLAME)),
                    "Можно вести полосой: отметьте всех на линии. Мягкая броня защищает только от 16 SP"),

            gun(EXOTIC, "Techtronica 15 Microwaver", "P", 0, "J", "P", "1D6", null, 10, 2, "VR", 20, 400),
            gun(EXOTIC, "Militech Elect. LaserCannon", "RIF", 0, "N", "R", "1-5D6", null, 10, 2, "UR", 200, 8000),
            gun(EXOTIC, "Avante P-1135 Needlegun", "P", 0, "P", "P", "Drugs", Cp2020Ammo.NEEDLES, 15, 2, "ST", 40, 200),
            gun(EXOTIC, "Enertex AKM Power Squirt", "P", -2, "J", "C", "Drugs", null, 50, 1, "VR", 10, 15),
            gun(EXOTIC, "Nelspot \"Wombat\"", "P", -1, "J", "C", "Drugs", Cp2020Ammo.PELLETS, 20, 2, "UR", 40, 200),
            gun(EXOTIC, "Miltech Electronics Taser", "P", -1, "J", "C", "Stun", null, 10, 1, "ST", 10, 60),
            directLoad(gun(EXOTIC, "EagleTech \"Tomcat\" C-Bow", "EX", 0, "N", "C", "4D6", Cp2020Ammo.ARROWS, 12, 1, "VR", 150, 150)),
            directLoad(gun(EXOTIC, "EagleTech \"Stryker\" X-Bow", "EX", -1, "N", "C", "3D6+3", Cp2020Ammo.BOLTS, 12, 1, "VR", 50, 220)),

            withProperty(gun(MONO, "Kendachi Monoknife", "MELEE", 1, "P", "P", "2D6", null, null, 1, "VR", 1, 200), Cp2020ItemTypes.EDGE, "MONO"),
            withProperty(gun(MONO, "Kendachi MonoKatana", "MELEE", 1, "N", "R", "4D6", null, null, 1, "VR", 1, 600), Cp2020ItemTypes.EDGE, "MONO"),
            gun(MONO, "SPM-1 Battleglove", "MELEE", -2, "N", "P", "3D6/2D6", null, null, 1, "VR", 1, 900),

            melee("Club", 0, "L", "1D6", 1, 0),
            // HOMEBREW: книга (с. 99) пишет «edged weapons», не перечисляя; клинками считаем всё с заточенным лезвием
            blade(note(withFlag(melee("Knife", 0, "P", "1D6", 1, 1), Cp2020ItemTypes.THROWN), "Цена по книге: 1–20")),
            blade(note(melee("Sword", 0, "N", "2D6+2", 1, 20), "Цена по книге: 20–200")),
            blade(melee("Axe", -1, "N", "2D6+3", 1, 20)),
            melee("Nunchaku/Tonfa", 0, "L", "3D6", 1, 15),
            blade(melee("Naginata", 0, "N", "3D6", 2, 100, "P")),
            blade(note(withFlag(melee("Shiriken", 0, "P", "1D6/3", null, 2), Cp2020ItemTypes.THROWN), THROWN + ". Цена по книге: 2–3")),
            blade(melee("Switchblade", 0, "P", "1D6/2", 1, 15)),
            melee("Brass Knuckles", 0, "P", "1D6+2", 1, 10),
            melee("Sledgehammer", -1, "N", "4D6", 1, 20),
            blade(melee("Chainsaw", -3, "N", "4D6", 2, 80)),

            directLoad(old("S&W Combat Magnum", "P", 1, "J", "C", "2D6+3", ".357", 6, 2, "VR")),
            directLoad(old("Llama Commanche", "P", 0, "J", "C", "4D6", ".44", 6, 1, "ST")),
            directLoad(old("Colt .45 \"Peacemaker\"", "P", 0, "J", "R", "2D6+2", ".45", 6, 1, "VR")),
            directLoad(old("Colt .38 Detective", "P", 1, "J", "C", "1D6+2", ".38", 6, 1, "VR")),
            directLoad(old("C.O.P. .357 Derringer", "P", 0, "P", "C", "2D6+3", ".357", 4, 2, "VR")),
            old("UZI", "SMG", 2, "J", "C", "2D6+1", "9mm", 30, 20, "VR"),
            old("Vz61 Skorpion", "SMG", 2, "J", "P", "1D6", ".25", 20, 25, "VR"),
            old("Ingram MAC 10", "SMG", -1, "J", "C", "2D6+2", ".45", 30, 5, "UR"),
            old("H&K MP5 & MP5K", "SMG", 1, "L", "C", "2D6+1", "9mm", 30, 20, "ST"),
            old("Thompson M1", "SMG", 2, "N", "C", "2D6+2", ".45", 30, 20, "VR"),
            // скрытность в книге напечатана как «C», такого кода нет
            old("Bushmaster", "SMG", 0, null, "R", "4D6", "5.56", 30, 20, "ST"),
            old("FN-FAL", "RIF", 0, "N", "E", "6D6+2", "7.56", 20, 21, "VR"),
            old("AK 47, AKM, AKMS", "RIF", 0, "N", "E", "5D6", "7.56S", 30, 20, "VR"),
            old("M-16A & M-16A2", "RIF", 2, "N", "C", "4D6", "5.56", 30, 25, "UR"),
            old("Styer Aug", "RIF", 2, "N", "C", "4D6", "5.56", 30, 20, "VR"),
            directLoad(old("Winchester M70", "RIF", 3, "N", "C", "5D6+1", "30-06", 5, 1, "VR")),
            // HOMEBREW: калибр CAWS в книге не указан, как у большинства — 12
            withProperty(old("CAWS", "SHT", 0, "N", "R", "4D6", "00", 10, 10, "ST"), Cp2020ItemTypes.GAUGE, "12"));

    private Cp2020Weapons() {
    }

    private static CatalogItem gun(String group, String name, String category, int accuracy, String concealability,
                                   String availability, String damage, String caliber, Integer shots, Integer rof,
                                   String reliability, Integer range, int cost) {
        Map<String, String> properties = new HashMap<>();
        put(properties, "category", category);
        put(properties, "accuracy", accuracy);
        put(properties, "concealability", concealability);
        put(properties, "availability", availability);
        put(properties, "damage", damage);
        put(properties, Cp2020ItemTypes.CALIBER, caliber);
        put(properties, Cp2020ItemTypes.SHOTS, shots);
        // HOMEBREW: новое оружие продаётся со снаряжённым магазином, книга об этом молчит
        put(properties, Cp2020ItemTypes.LOADED, shots);
        put(properties, Cp2020ItemTypes.RATE_OF_FIRE, rof);
        put(properties, "reliability", reliability);
        put(properties, "range", range);
        return new CatalogItem(key(name), group, name, Cp2020ItemTypes.WEAPON, cost, null, properties, 1, shots != null);
    }

    /** Граната: дальность от BODY, зона 5 м (с. 99); виды и эффекты — с. 63 */
    private static CatalogItem grenade(String kind, String damage, String description) {
        CatalogItem grenade = gun(HEAVY, "Grenade: " + kind, "HVY", 0, "P", "P", damage, null, null, 1, "VR", null, 30);
        CatalogItem armed = withFlag(withFlag(withProperty(grenade, Cp2020ItemTypes.AREA, "5"), Cp2020ItemTypes.THROWN), Cp2020ItemTypes.PLANTED);
        return note(armed, description == null ? THROWN : THROWN + ". " + description);
    }

    /** Взрывчатка: зона на единицу, урон по всему телу (с. 111) */
    private static CatalogItem explosive(String name, String damage, int area, String unit, boolean planted, String description) {
        CatalogItem item = gun(HEAVY, name, "HVY", 0, "P", "P", damage, null, null, 1, "VR", null, 0);
        CatalogItem armed = withProperty(withProperty(withFlag(withFlag(item, Cp2020ItemTypes.THROWN), Cp2020ItemTypes.EXPLOSIVE),
                Cp2020ItemTypes.AREA, String.valueOf(area)), Cp2020ItemTypes.UNIT, unit);
        return note(planted ? withFlag(armed, Cp2020ItemTypes.PLANTED) : armed, description);
    }

    /** Газ (с. 110): сразу все в 3 м, а не 5 м обычной гранаты */
    private static CatalogItem gas(String kind, String damage, String description) {
        return withProperty(grenade("Gas, " + kind, damage, description + ". " + GAS_NOTE + ". Штраф ведёт мастер"), Cp2020ItemTypes.AREA, "3");
    }

    private static CatalogItem deadlyGas(String kind, String damage) {
        return withProperty(withProperty(grenade("Gas, " + kind, damage, "При спасброске половина урона. " + GAS_NOTE),
                Cp2020ItemTypes.AREA, "3"), Cp2020ItemTypes.EFFECT, Cp2020Effects.GAS);
    }

    private static CatalogItem old(String name, String category, int accuracy, String concealability, String availability,
                                   String damage, String caliber, int shots, int rof, String reliability) {
        return note(gun(OLD, name, category, accuracy, concealability, availability, damage, caliber, shots, rof,
                reliability, null, 0), OLD_NOTE);
    }

    private static CatalogItem melee(String name, int accuracy, String concealability, String damage, Integer range, int cost) {
        return melee(name, accuracy, concealability, damage, range, cost, "C");
    }

    private static CatalogItem melee(String name, int accuracy, String concealability, String damage, Integer range,
                                     int cost, String availability) {
        return gun(MELEE, name, "MELEE", accuracy, concealability, availability, damage, null, null, null, null, range, cost);
    }

    private static CatalogItem note(CatalogItem item, String description) {
        return new CatalogItem(item.key(), item.group(), item.name(), item.typeCode(), item.cost(), description,
                item.properties(), item.packSize(), item.individual());
    }

    private static CatalogItem blade(CatalogItem item) {
        return withProperty(item, Cp2020ItemTypes.EDGE, "BLADE");
    }

    private static CatalogItem armorPiercing(CatalogItem item) {
        return withFlag(item, Cp2020ItemTypes.ARMOR_PIERCING);
    }

    private static CatalogItem directLoad(CatalogItem item) {
        return withFlag(item, Cp2020ItemTypes.DIRECT_LOAD);
    }

    private static CatalogItem armorWear(CatalogItem item, int points) {
        return withProperty(item, Cp2020ItemTypes.ARMOR_WEAR, String.valueOf(points));
    }

    private static CatalogItem withFlag(CatalogItem item, String flag) {
        return withProperty(item, flag, "1");
    }

    private static CatalogItem withProperty(CatalogItem item, String key, String value) {
        Map<String, String> properties = new HashMap<>(item.properties());
        properties.put(key, value);
        return new CatalogItem(item.key(), item.group(), item.name(), item.typeCode(), item.cost(), item.description(),
                properties, item.packSize(), item.individual());
    }

    private static void put(Map<String, String> properties, String key, Object value) {
        if (value != null) {
            properties.put(key, value.toString());
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
