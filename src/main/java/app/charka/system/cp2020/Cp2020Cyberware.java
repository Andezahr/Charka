package app.charka.system.cp2020;

import app.charka.item.CatalogItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Справочник киберware: таблица книги (с. 76–79), значения прочитаны с изображений страниц. Описания — своими словами и коротко, подробности в книге.
 * Порядок значений как в таблице: название, хирургия, цена, потеря человечности, описание. Цена-диапазон — нижняя граница, диапазон в описании.
 */
public final class Cp2020Cyberware {

    static final String PROCESSOR = "Neural Processor";
    private static final String SOCKET = "Chipware Socket";
    private static final String OPTIC = "Cyberoptic";
    private static final String AUDIO = "Cyberaudio";
    private static final String LIMB = "Cyberarm|Cyberleg";

    private static final String FASHION = "Модная";
    private static final String NEURAL = "Нейроware";
    private static final String IMPLANTS = "Импланты";
    private static final String BIO = "Биоware";
    private static final String WEAPONS = "Кибероружие";
    private static final String OPTICS = "Кибероптика";
    private static final String AUDIOS = "Киберслух";
    private static final String LIMBS = "Киберконечности";
    private static final String HANDS = "Кисти и стопы";
    private static final String BUILT_IN = "Встроено в конечность";
    private static final String LIMB_WEAPONS = "Оружие в конечности";
    private static final String FRAMES = "Линейные рамы";
    private static final String PLATING = "Бронепластины";

    private static final String BLADE = Cp2020DamageType.BLADE.name();
    private static final String MONO = Cp2020DamageType.MONO.name();

    public static final List<CatalogItem> CATALOG = List.of(
            cyber(FASHION, "Biomonitor", "N", 100, "1", "+2 к сопротивлению пыткам и наркотикам"),
            cyber(FASHION, "Skinwatch", "N", 50, "1", "Часы под кожей"),
            cyber(FASHION, "Light Tattoo", "N", 1, "0.5", "Светящаяся татуировка. Цена 1–20"),
            cyber(FASHION, "Shift-tacts", "N", 1, "0.5", "Линзы меняют цвет. Цена 1–200"),
            cyber(FASHION, "ChemSkins", "N", 200, "1D6/2", "Цвет и рисунок кожи"),
            cyber(FASHION, "Synthskins", "N", 400, "1D6", "Искусственная кожа меняет цвет и рисунок"),
            cyber(FASHION, "Techhair", "M", 1, "2", "Светящиеся волосы. Цена 1–200"),

            cyber(NEURAL, PROCESSOR, "M", 1000, "1D6", "Основа всего нейроware"),
            neural(initiative(cyber(NEURAL, "Kerenzikov +1", "N", 500, "1D6", "Ускоритель всегда включён"), 1)),
            neural(initiative(cyber(NEURAL, "Kerenzikov +2", "N", 1000, "2D6", "Ускоритель всегда включён. Цена за 2 уровня"), 2)),
            neural(with(cyber(NEURAL, Cp2020Cyber.SPEEDWARE, "N", 1600, "1D6/2", "Sandevistan: включается в бою, раунд ожидания, затем +3 к инициативе на 5 раундов"), Cp2020ItemTypes.SPEEDWARE, "1")),
            neural(cyber(NEURAL, "Tactile Boost", "N", 100, "2", "+2 к Внимательности на ощупь")),
            neural(cyber(NEURAL, "Olfactory Boost", "N", 100, "2", "+2 к Внимательности по запаху, след по запаху")),
            neural(cyber(NEURAL, "Pain Editor", "N", 200, "2D6", "Без боли, Выносливость на две ступени легче")),
            neural(cyber(NEURAL, "Cybermodem Link", "N", 100, "1", "Прямое подключение к кибермодему")),
            neural(cyber(NEURAL, "Vehicle Link", "N", 100, "3", "Прямое управление машиной")),
            neural(cyber(NEURAL, "Smartgun Link", "N", 100, "2", "Прямое управление смартганом")),
            neural(cyber(NEURAL, "Machine/Tech Link", "N", 100, "2", "Управление станками и машинами")),
            neural(cyber(NEURAL, "DataTerm Link", "N", 100, "2", "Загрузка с DataTerm в память")),
            neural(cyber(NEURAL, "Interface Plugs", "M", 200, "1D6", "Подключение к смартгану и машине. Потеря за пару")),
            neural(cyber(NEURAL, SOCKET, "N", 200, "1D6/2", "До 10 чипов")),
            requires(cyber(NEURAL, "Reflex Chip", "N", 0, "0", "Навык REF или TECH с ручной работой, +1…+3, заменяет природный уровень. Цена разная"), SOCKET),
            requires(cyber(NEURAL, "Memory Chip", "N", 0, "0", "Навык INT, +1…+3, заменяет природный уровень. Цена разная"), SOCKET),

            cyber(IMPLANTS, "Nasal Filters", "M", 60, "2", "Останавливают газы, 70%"),
            cyber(IMPLANTS, "Gills", "MA", 400, "3D6", "Дышать под водой 4 часа"),
            cyber(IMPLANTS, "Independent Air Supply", "MA", 300, "2D6", "Воздух на 25 минут"),
            cyber(IMPLANTS, "Mr Studd Sexual Implant", "MA", 300, "2D6", "Всю ночь"),
            cyber(IMPLANTS, "Contraceptive Implant", "N", 100, "0.5", "На 5 лет, 98%"),
            cyber(IMPLANTS, "SubDermal Pocket", "M", 200, "2D6", "Карман 5×10 см под кожей"),
            cyber(IMPLANTS, "Adrenal Booster", "M", 400, "2D6", "+1 REF на 1D6+2 хода, 3 раза в день"),
            // с. 101: Subdermal считается слоем брони
            armor(cyber(IMPLANTS, "Subdermal Armor", "CR", 1200, "2D6", "Торс SP 18"), 18, Cp2020ItemTypes.HARD, Cp2020Location.TORSO),
            cyber(IMPLANTS, "Motion Detector", "M", 200, "2D6", "Движение в 20 м², 70%"),
            cyber(IMPLANTS, "Digital Recorder", "M", 200, "2", "2 часа записи"),
            cyber(IMPLANTS, "Audio/Video Tape Recorder", "M", 300, "2", "2 часа записи со слуха и зрения"),
            requires(cyber(IMPLANTS, "Radar Sensor", "M", 200, "2", "Радар 100 м, 70%"), OPTIC),
            cyber(IMPLANTS, "Sonar Implant", "M", 300, "2", "Сонар 50 м, только в воде, 70%"),
            cyber(IMPLANTS, "Radiation Detector", "M", 200, "2", "10 м, 80%"),
            cyber(IMPLANTS, "Chemical Analyser", "M", 200, "2", "5 м, 70%"),
            cyber(IMPLANTS, "Voice Synthesizer", "M", 600, "1D6", "Подражает записанному звуку, 60%"),
            cyber(IMPLANTS, "AudioVox", "M", 700, "2D6", "Спецэффекты голоса, +2 к Выступлению"),

            body(cyber(BIO, "Grafted Muscle +1", "MA", 1000, "2D6", "До +2 BODY всего, складывается с Lace"), 1),
            body(cyber(BIO, "Grafted Muscle +2", "MA", 2000, "2D6", "До +2 BODY всего, складывается с Lace"), 2),
            body(cyber(BIO, "Muscle and Bone Lace", "N", 1500, "1D6/2", "+2 BODY"), 2),
            // с. 101: Skinweave — слой без штрафа
            with(armor(cyber(BIO, "Skin Weave", "N", 2000, "2D6", "Всё тело SP 12, слой без штрафа"), 12, Cp2020ItemTypes.SOFT, Cp2020Location.values()),
                    Cp2020ItemTypes.NO_LAYER_PENALTY, "1"),
            cyber(BIO, "Enhanced Antibodies", "N", 3000, "1D6/2", "+1 очко лечения в день"),
            cyber(BIO, "Toxin Binders", "N", 3000, "1D6/2", "+4 к спасброскам от яда и наркотиков"),
            cyber(BIO, "Nanosurgeons", "N", 6000, "1D6/2", "Лечение вдвое быстрее"),

            weapon(cyber(WEAPONS, "Scratchers", "M", 100, "2D6", "Когти в руках"), "1D6/2", null, false),
            weapon(cyber(WEAPONS, "Implanted Fangs (Vampires)", "M", 200, "3D6", "Клыки"), "1D6/3", null, false),
            weapon(cyber(WEAPONS, "Rippers", "M", 400, "3D6", "Лезвия в руках, против брони как нож"), "1D6+3", BLADE, false),
            weapon(cyber(WEAPONS, "Wolvers", "M", 600, "3D6+1", "Лезвия в руках, против брони как нож"), "3D6", BLADE, false),
            weapon(cyber(WEAPONS, "Big Knucks", "M", 500, "3D6", "Усиленные кулаки"), "1D6+2", null, false),
            weapon(cyber(WEAPONS, "Slice N' Dice", "M", 700, "3D6", "Моно-струна в пальце"), "2D6", MONO, false),
            weapon(cyber(WEAPONS, "Cybersnake", "MA", 1200, "4D6", "Управляется сама: одна атака в раунд, навык 10 + 1D10"), "1D6", null, false),

            cyber(OPTICS, OPTIC, "MA", 500, "2D6", "Базовый модуль глаза, до 4 опций. Цена и потеря за глаз"),
            option(OPTICS, OPTIC, "Color Shift", 300, "0.5", "Меняет цвет"),
            option(OPTICS, OPTIC, "Image Enhancement", 300, "1", "+2 к Внимательности зрением"),
            option(OPTICS, OPTIC, "Targeting Scope", 400, "2", "+1 ко всем атакам со смартганом"),
            option(OPTICS, OPTIC, "Times Square Marquee", 300, "1", "Текст в поле зрения"),
            option(OPTICS, OPTIC, "Teleoptics", 150, "0.5", "Приближение ×20"),
            option(OPTICS, OPTIC, "Micro-optics", 150, "0.5", "Микроскоп"),
            option(OPTICS, OPTIC, "Anti Dazzle", 200, "0.5", "Не слепит вспышка и лазер"),
            option(OPTICS, OPTIC, "Low Lite", 200, "0.5", "Видит при тусклом свете"),
            option(OPTICS, OPTIC, "Thermograph Sensor", 200, "1", "Видит тепло"),
            option(OPTICS, OPTIC, "Infrared", 200, "1", "Видит в полной темноте"),
            option(OPTICS, OPTIC, "Ultraviolet", 200, "1", "Видит в УФ"),
            option(OPTICS, OPTIC, "MicroVideo Optic", 300, "0.5", "Видео 20 минут, 2 места опций"),
            option(OPTICS, OPTIC, "Digital Camera", 300, "0.5", "20 снимков, 2 места опций"),
            option(OPTICS, OPTIC, "Dartgun", 200, "2", "1 дротик, 3 места опций"),

            cyber(AUDIOS, AUDIO, "M", 500, "2D6", "Базовый модуль слуха, опций сколько угодно"),
            option(AUDIOS, AUDIO, "Amplified Hearing", 200, "1", "+1 к Внимательности на слух"),
            option(AUDIOS, AUDIO, "Radio Link", 100, "1", "Радио на 1 милю"),
            option(AUDIOS, AUDIO, "Phone Splice", 150, "1", "Сотовая связь"),
            option(AUDIOS, AUDIO, "Scrambler", 100, "0.5", "Разговор не подслушать"),
            option(AUDIOS, AUDIO, "Bug Detector", 200, "0.5", "Жучки в 3 м, 60%"),
            option(AUDIOS, AUDIO, "Voice Stress Analyser", 200, "1", "Детектор лжи, +2 к Эмпатии и Допросу"),
            option(AUDIOS, AUDIO, "Sound Editing", 150, "0.5", "+2 к Внимательности к одному разговору"),
            option(AUDIOS, AUDIO, "Enhanced Hearing Range", 150, "2", "Ультразвук и инфразвук"),
            option(AUDIOS, AUDIO, "Wearman", 100, "0.5", "Музыка"),
            option(AUDIOS, AUDIO, "Radar Detector", 150, "0.5", "Пищит от радара, 40% источник"),
            option(AUDIOS, AUDIO, "Homing Tracer", 200, "0.5", "Маячок на 1 км"),
            option(AUDIOS, AUDIO, "Tight Beam Radio Link", 200, "1", "Радио в прямой видимости, не перехватить"),
            option(AUDIOS, AUDIO, "Wide Band Radio Scanner", 100, "2", "Слушает все диапазоны"),
            option(AUDIOS, AUDIO, "Micro-recorder Link", 100, "0.5", "Передаёт на запись"),
            option(AUDIOS, AUDIO, "Digital Recording Link", 100, "0.5", "Передаёт на цифровую запись"),
            option(AUDIOS, AUDIO, "Level Damper", 300, "0.5", "Гасит громкий звук"),

            cyber(LIMBS, "Cyberarm", "CR", 3000, "2D6", "До 4 опций. 20 урона — не работает, 30 — уничтожена"),
            cyber(LIMBS, "Cyberleg", "CR", 2000, "2D6", "До 3 опций. 20 урона — не работает, 30 — уничтожена"),
            option(LIMBS, LIMB, "Quick Change Mount", 200, "2", "Смена кибера за ход"),
            option(LIMBS, LIMB, "Hydraulic Rams", 200, "3", "Прочность 30, урон ×3"),
            option(LIMBS, LIMB, "Thickened Myomar", 250, "2", "Прочность 25, урон ×2, прыжок +50%"),
            option(LIMBS, LIMB, "Reinforced Joints", 200, "1", "Прочность +5"),
            requires(cyber(LIMBS, "Artificial Shoulder Mount", "CR", 1500, "2D6", "Ещё 2 руки ниже первой пары"), LIMB),
            option(LIMBS, LIMB, "Microwave/EMP Shielding", 300, "1", "Не страдает от микроволн"),
            option(LIMBS, LIMB, "Plastic Covering", 1, "1", "Цветной, прозрачный. Цена 1–200"),
            option(LIMBS, LIMB, "RealSkinn", 200, "0", "Как настоящая, снижает потерю на 1D6/2"),
            option(LIMBS, LIMB, "Superchrome", 200, "3", "Блестящий металл"),
            option(LIMBS, LIMB, "Limb Armor", 200, "1D6", "Конечность SP 20"),

            option(HANDS, LIMB, "Standard Hand", 150, "0", "Обычная кисть"),
            weapon(option(HANDS, LIMB, "Ripper Hand", 600, "2D6", "Кисть с лезвиями, против брони как нож"), "1D6+3", BLADE, false),
            weapon(option(HANDS, LIMB, "HammerHand", 600, "2D6", "Гидравлический кулак"), "1D10", null, false),
            weapon(option(HANDS, LIMB, "BuzzHand", 600, "2D6", "Пила, −2 SP мягкой брони за удар: снимите вручную"), "2D6+2", null, false),
            option(HANDS, LIMB, "Tool Hand", 200, "2", "Инструменты в пальцах"),
            option(HANDS, LIMB, "Grapple Hand", 350, "3", "Кошка на 30 м"),
            option(HANDS, LIMB, "Extension Hand", 350, "2", "Кисть выдвигается на 1 м"),
            weapon(option(HANDS, LIMB, "Spike Hand", 500, "2D6", "Шип из ладони"), "1D6+3", null, true),
            option(HANDS, LIMB, "Modular Hand", 600, "2", "4 инструмента на выбор"),
            option(HANDS, LIMB, "Standard Foot", 200, "0", "Обычная стопа"),
            weapon(option(HANDS, LIMB, "Talon Foot", 600, "2D6", "Лезвия в пальцах, против брони как нож"), "1D6", BLADE, false),
            option(HANDS, LIMB, "Tool Foot", 300, "2", "Инструменты в пальцах"),
            option(HANDS, LIMB, "Web Foot", 500, "2", "Плавание вдвое быстрее, +3 к Плаванию"),
            option(HANDS, LIMB, "Grip Foot", 500, "2", "+2 к Лазанию"),
            weapon(option(HANDS, LIMB, "Spike Heel Foot", 500, "2D6", "Шип в пятке"), "2D6", null, true),

            option(BUILT_IN, LIMB, "AV Tape Recorder", 250, "1", "2 часа записи, нужен внешний приёмник"),
            option(BUILT_IN, LIMB, "Cybermodem", 3000, "1", "Встроенная кибердека. Сотовая версия 5000"),
            option(BUILT_IN, LIMB, "Limb Digital Recorder", 300, "1", "Цифровая запись на чип"),
            option(BUILT_IN, LIMB, "Storage Space", 50, "0.5", "Тайник 5×15 см"),
            option(BUILT_IN, LIMB, "MiniCam", 200, "2", "Камера на 20 снимков"),
            option(BUILT_IN, LIMB, "MiniVid", 400, "2", "Видео 30 минут"),
            option(BUILT_IN, LIMB, "Hidden Holster", 100, "1", "Размер оружия по BODY"),
            option(BUILT_IN, LIMB, "LCD Screen Readout", 200, "1", "Экран для любого устройства"),
            option(BUILT_IN, LIMB, "Techscanner", 400, "3", "Как сканер, с. 59"),

            // Оружие в конечности стреляет как обычное: заведите его отдельным предметом
            option(LIMB_WEAPONS, LIMB, "Limb Grenade Launcher", 500, "2D6", "1 граната любого типа"),
            option(LIMB_WEAPONS, LIMB, "Micro-Missile Launcher", 900, "2D6", "4 микроракеты, 4D6 каждая"),
            option(LIMB_WEAPONS, LIMB, "Popup Gun", 2, "2D6", "Размер по BODY. Цена 2–800"),
            option(LIMB_WEAPONS, LIMB, "Limb Flame Thrower", 600, "2D6", "2D6, затем 1D6/2 ещё 2 хода"),
            option(LIMB_WEAPONS, LIMB, "Weapon Mount & Link", 100, "3", "Крепление и нейросвязь на 1 оружие"),
            option(LIMB_WEAPONS, LIMB, "2 Shot Capacitor Laser", 800, "2D6", "Лазер в плече, 3D6, 2 выстрела"),

            strength(cyber(FRAMES, "Linear Frame Σ", "MA", 6000, "2D6", "Сила 12 вместо BODY"), 12),
            strength(cyber(FRAMES, "Linear Frame β", "MA", 8000, "2D6", "Сила 14 вместо BODY"), 14),
            strength(cyber(FRAMES, "Linear Frame Ω", "MA", 10000, "3D6", "Сила 16 вместо BODY"), 16),

            // с. 101: Bodyplating считается слоем брони
            armor(cyber(PLATING, "Cowl", "MA", 200, "1D6", "Голова SP 25"), 25, Cp2020ItemTypes.HARD, Cp2020Location.HEAD),
            armor(cyber(PLATING, "Faceplate", "CR", 400, "4D6", "Голова SP 25"), 25, Cp2020ItemTypes.HARD, Cp2020Location.HEAD),
            with(armor(cyber(PLATING, "Torso Plate", "MA", 2000, "3D6", "Торс SP 25, −3 REF"), 25, Cp2020ItemTypes.HARD, Cp2020Location.TORSO),
                    Cp2020ItemTypes.ENCUMBRANCE, "3"),
            cyber(PLATING, "Front Optic Mount", "MA", 1000, "4D6", "До 5 оптик на лице"),
            cyber(PLATING, "Sense Extensions (Rabbit Ears)", "M", 500, "3D6", "Выносные слух и зрение"));

    private Cp2020Cyberware() {
    }

    private static CatalogItem cyber(String group, String name, String surgery, int cost, String humanity, String description) {
        Map<String, String> properties = new HashMap<>();
        properties.put(Cp2020ItemTypes.SURGERY, surgery);
        properties.put(Cp2020ItemTypes.HUMANITY_COST, humanity);
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9σβω]+", "_").replaceAll("^_|_$", "");
        return new CatalogItem("cyber_" + slug, group, name, Cp2020ItemTypes.CYBER, cost, description, properties, 1, true);
    }

    /** Опция базового модуля: хирургия всегда Negligible (с. 76–79) */
    private static CatalogItem option(String group, String parent, String name, int cost, String humanity, String description) {
        return requires(cyber(group, name, "N", cost, humanity, description), parent);
    }

    private static CatalogItem neural(CatalogItem item) {
        return requires(item, PROCESSOR);
    }

    private static CatalogItem requires(CatalogItem item, String parent) {
        return with(item, Cp2020ItemTypes.REQUIRES, parent);
    }

    private static CatalogItem initiative(CatalogItem item, int bonus) {
        return with(item, Cp2020ItemTypes.INITIATIVE, String.valueOf(bonus));
    }

    private static CatalogItem body(CatalogItem item, int bonus) {
        return with(item, Cp2020ItemTypes.BODY_BONUS, String.valueOf(bonus));
    }

    private static CatalogItem strength(CatalogItem item, int value) {
        return with(item, Cp2020ItemTypes.STRENGTH, String.valueOf(value));
    }

    private static CatalogItem armor(CatalogItem item, int sp, String hardness, Cp2020Location... zones) {
        CatalogItem armored = with(with(item, Cp2020ItemTypes.ARMOR_SP, String.valueOf(sp)), Cp2020ItemTypes.HARDNESS, hardness);
        for (Cp2020Location zone : Arrays.asList(zones)) {
            armored = with(armored, Cp2020ItemTypes.zoneKey(zone), "1");
        }
        return armored;
    }

    private static CatalogItem weapon(CatalogItem item, String damage, String edge, boolean armorPiercing) {
        CatalogItem armed = with(item, "damage", damage);
        if (edge != null) {
            armed = with(armed, Cp2020ItemTypes.EDGE, edge);
        }
        return armorPiercing ? with(armed, Cp2020ItemTypes.ARMOR_PIERCING, "1") : armed;
    }

    private static CatalogItem with(CatalogItem item, String key, String value) {
        Map<String, String> properties = new HashMap<>(item.properties());
        properties.put(key, value);
        return new CatalogItem(item.key(), item.group(), item.name(), item.typeCode(), item.cost(), item.description(), properties, item.packSize(), item.individual());
    }

    /** Строка справочника по названию: для быстрых NPC */
    static String keyOf(String name) {
        return new ArrayList<>(CATALOG).stream().filter(row -> row.name().equals(name)).findFirst().orElseThrow().key();
    }
}
