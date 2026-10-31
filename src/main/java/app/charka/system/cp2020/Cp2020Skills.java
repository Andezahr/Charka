package app.charka.system.cp2020;

import app.charka.skill.CharacterRole;
import app.charka.skill.SkillDefinition;

import java.util.List;

import static app.charka.system.cp2020.Cp2020StatsStrategy.*;

/**
 * Каталог навыков и ролей Cyberpunk 2020 в порядке книги: спецспособности ролей, затем навыки по статам.
 */
public final class Cp2020Skills {

    public static final List<SkillDefinition> CATALOG = List.of(
            special("authority", "Авторитет", COOL),
            special("charismatic_leadership", "Харизматичное лидерство", COOL),
            special("combat_sense", "Боевое чутьё", INT),
            special("credibility", "Доверие", INT),
            special("family", "Семья", INT),
            special("interface", "Интерфейс", INT),
            special("jury_rig", "Кустарный ремонт", TECH),
            special("medical_tech", "Медицинская техника", TECH),
            special("resources", "Ресурсы", INT),
            special("streetdeal", "Уличные сделки", COOL),

            skill("personal_grooming", "Уход за собой", ATTR),
            skill("wardrobe_style", "Гардероб и стиль", ATTR),

            skill("endurance", "Выносливость", BODY),
            skill("strength_feat", "Силовые трюки", BODY),
            skill("swimming", "Плавание", BODY),

            skill("interrogation", "Допрос", COOL),
            skill("intimidate", "Запугивание", COOL),
            skill("oratory", "Ораторство", COOL),
            skill("resist_torture", "Стойкость к пыткам и наркотикам", COOL),
            skill("streetwise", "Знание улиц", COOL),

            skill("human_perception", "Понимание людей", EMP),
            skill("interview", "Интервью", EMP),
            skill("leadership", "Лидерство", EMP),
            skill("seduction", "Соблазнение", EMP),
            skill("social", "Светское общение", EMP),
            skill("persuasion", "Убеждение и болтовня", EMP),
            skill("perform", "Выступление", EMP),

            skill("accounting", "Бухгалтерия", INT),
            skill("anthropology", "Антропология", INT),
            skill("awareness", "Внимательность", INT),
            skill("biology", "Биология", INT),
            skill("botany", "Ботаника", INT),
            skill("chemistry", "Химия", INT),
            skill("composition", "Сочинительство", INT),
            skill("diagnose", "Диагностика болезней", INT),
            skill("education", "Образование и общие знания", INT),
            custom("expert", "Эксперт", INT, 1),
            skill("gamble", "Азартные игры", INT),
            skill("geology", "Геология", INT),
            skill("hide_evade", "Укрытие и уклонение от погони", INT),
            skill("history", "История", INT),
            language("germanic", "Германские: английский, немецкий, шведский…", 1),
            language("romantic", "Романские: французский, испанский, итальянский…", 1),
            language("semitic", "Семитские: арабский, иврит", 1),
            language("african", "Африканские: суахили, зулу, банту…", 2),
            language("baltic", "Балтийские: литовский, эстонский, финский…", 2),
            language("celtic", "Кельтские: гэльский, валлийский, бретонский", 2),
            language("farsi", "Фарси", 2),
            language("greek", "Греческий", 2),
            language("japanese", "Японский", 2),
            language("korean", "Корейский", 2),
            language("pacific", "Тихоокеанские: тагальский, малайский, гавайский…", 2),
            language("sino_tibetan", "Китайско-тибетские: кантонский, мандарин, тайский…", 3),
            language("slavic", "Славянские: русский, польский, чешский…", 3),
            skill("library_search", "Поиск информации", INT),
            skill("mathematics", "Математика", INT),
            skill("physics", "Физика", INT),
            skill("programming", "Программирование", INT),
            skill("shadow_track", "Слежка и выслеживание", INT),
            skill("stock_market", "Биржа", INT),
            skill("system_knowledge", "Знание Сети", INT),
            skill("teaching", "Преподавание", INT),
            skill("wilderness_survival", "Выживание в дикой местности", INT),
            skill("zoology", "Зоология", INT),

            skill("archery", "Стрельба из лука", REF),
            skill("athletics", "Атлетика", REF),
            skill("brawling", "Драка", REF),
            skill("dance", "Танец", REF),
            skill("dodge_escape", "Уклонение и освобождение", REF),
            skill("driving", "Вождение", REF),
            skill("fencing", "Фехтование", REF),
            skill("handgun", "Пистолеты", REF),
            skill("heavy_weapons", "Тяжёлое оружие", REF),
            skill("aikido", "Айкидо", REF, 3),
            skill("animal_kung_fu", "Кунг-фу: стили животных", REF, 3),
            skill("boxing", "Бокс", REF, 1),
            skill("capoeira", "Капоэйра", REF, 3),
            skill("choi_li_fut", "Чой Ли Фут", REF, 3),
            skill("judo", "Дзюдо", REF, 1),
            skill("karate", "Карате", REF, 2),
            skill("savate", "Сават", REF, 2),
            skill("tae_kwon_do", "Тхэквондо", REF, 3),
            skill("thai_kick_boxing", "Тайский бокс", REF, 4),
            skill("wrestling", "Борьба", REF, 1),
            // стиля нет в книге: множитель её не задаёт (придуманный навык согласуют с мастером), ×2 — наше значение
            custom("martial_art", "Своё боевое искусство", REF, 2),
            skill("melee", "Холодное оружие", REF),
            skill("motorcycle", "Мотоцикл", REF),
            skill("heavy_machinery", "Тяжёлая техника", REF),
            skill("pilot_gyro", "Пилот: гирокоптер", REF, 3),
            skill("pilot_fixed_wing", "Пилот: самолёт", REF, 2),
            skill("pilot_dirigible", "Пилот: дирижабль", REF, 2),
            skill("pilot_vector_thrust", "Пилот: вектор тяги", REF, 3),
            skill("rifle", "Винтовки", REF),
            skill("stealth", "Скрытность", REF, 2),
            skill("submachinegun", "Пистолеты-пулемёты", REF),

            skill("aero_tech", "Авиатехника", TECH, 2),
            skill("av_tech", "Техника AV", TECH, 3),
            skill("basic_tech", "Базовая техника", TECH, 2),
            skill("cryotank", "Работа с криокамерой", TECH),
            skill("cyberdeck_design", "Проектирование кибердек", TECH, 2),
            skill("cybertech", "Кибертехника", TECH, 2),
            skill("demolitions", "Взрывное дело", TECH, 2),
            skill("disguise", "Маскировка", TECH),
            skill("electronics", "Электроника", TECH),
            skill("electronic_security", "Электронная защита", TECH, 2),
            skill("first_aid", "Первая помощь", TECH),
            skill("forgery", "Подделка", TECH),
            skill("gyro_tech", "Техника гирокоптеров", TECH, 3),
            skill("paint_draw", "Живопись и рисунок", TECH),
            skill("photo_film", "Фото и видео", TECH),
            skill("pharmaceuticals", "Фармацевтика", TECH, 2),
            skill("pick_lock", "Взлом замков", TECH),
            skill("pick_pocket", "Карманные кражи", TECH),
            skill("play_instrument", "Игра на инструменте", TECH),
            skill("weaponsmith", "Оружейное дело", TECH, 2));

    public static final List<CharacterRole> ROLES = List.of(
            new CharacterRole("solo", "Соло", "combat_sense"),
            new CharacterRole("rockerboy", "Рокербой", "charismatic_leadership"),
            new CharacterRole("netrunner", "Нетраннер", "interface"),
            new CharacterRole("media", "Медиа", "credibility"),
            new CharacterRole("nomad", "Кочевник", "family"),
            new CharacterRole("fixer", "Фиксер", "streetdeal"),
            new CharacterRole("cop", "Коп", "authority"),
            new CharacterRole("corporate", "Корпорат", "resources"),
            new CharacterRole("techie", "Техник", "jury_rig"),
            new CharacterRole("medtechie", "Медтехник", "medical_tech"));

    private Cp2020Skills() {
    }

    private static SkillDefinition skill(String code, String name, String stat) {
        return skill(code, name, stat, 1);
    }

    private static SkillDefinition skill(String code, String name, String stat, int cost) {
        return new SkillDefinition(code, name, stat, cost, false, false);
    }

    private static SkillDefinition custom(String code, String name, String stat, int cost) {
        return new SkillDefinition(code, name, stat, cost, false, true);
    }

    /**
     * Язык семьи: множитель и родство языков зависят от неё (книга, с. 49).
     */
    private static SkillDefinition language(String family, String variant, int cost) {
        return new SkillDefinition("language_" + family, "Язык", INT, cost, false, true, variant);
    }

    private static SkillDefinition special(String code, String name, String stat) {
        return new SkillDefinition(code, name, stat, 1, true, false);
    }
}
