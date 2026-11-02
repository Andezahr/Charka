package app.charka.system.cp2020;

import app.charka.creation.CharacterCreation;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.model.notes.Note;
import app.charka.model.notes.NoteCategory;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CounterRepository;
import app.charka.service.CharacterService;
import app.charka.service.MoneyService;
import app.charka.service.notes.NoteCategoryService;
import app.charka.service.notes.NoteService;
import app.charka.skill.CharacterRole;
import app.charka.skill.SkillDefinition;
import app.charka.skill.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Создание персонажа CP2020 по книге: очки характеристик (с. 20), девять статов от 2 до 10, роль и её 10 карьерных навыков
 * на 40 очков (с. 44), навыки хобби на REF + INT очков (с. 45), стартовые деньги: зарплата по таблице с. 58 × 1D6/3 месяцев.
 * Таблицы прочитаны с изображений страниц.
 */
@Component
@RequiredArgsConstructor
public class Cp2020Creation implements CharacterCreation {

    static final int MIN_STAT = 2;
    static final int MAX_STAT = 10;
    static final int CAREER_POINTS = 40;
    private static final int MAX_LEVEL = 10;
    private static final int STATS_COUNT = 9;
    /** Родной язык — на +8 (с. 34) */
    private static final int NATIVE_LANGUAGE = 8;
    private static final String LIFEPATH_CATEGORY = "Лайфпас";
    /** Колонка text заметки — 1000 символов */
    private static final int MAX_NOTE_TEXT = 1000;

    static final String RANDOM = "RANDOM";
    static final String FAST = "FAST";
    static final String MANUAL = "MANUAL";

    /** Порядок как на листе книги */
    static final List<String> STATS = List.of(Cp2020StatsStrategy.INT, Cp2020StatsStrategy.REF, Cp2020StatsStrategy.TECH,
            Cp2020StatsStrategy.COOL, Cp2020StatsStrategy.ATTR, Cp2020StatsStrategy.LUCK, Cp2020StatsStrategy.MA,
            Cp2020StatsStrategy.BODY, Cp2020StatsStrategy.EMP);

    /** Кинематографический способ (с. 20): мастер назначает очки по месту персонажа в игре */
    static final Map<String, Integer> CINEMATIC = new LinkedHashMap<>();

    static {
        CINEMATIC.put("Major Hero", 80);
        CINEMATIC.put("Minor Hero", 75);
        CINEMATIC.put("Major Supporting Character", 70);
        CINEMATIC.put("Minor Supporting Character", 60);
        CINEMATIC.put("Average", 50);
    }

    /** Слот карьерного пакета: один навык или выбор из нескольких */
    public record Slot(String label, List<SkillDefinition> options) {

        public boolean isChoice() {
            return options.size() > 1;
        }
    }

    /**
     * @param salaries месячная зарплата по уровню спецспособности: 1–5, 6, 7, 8, 9, 10
     */
    public record Role(CharacterRole role, List<Slot> slots, List<Integer> salaries) {
    }

    /** Всё для страницы */
    public record Form(List<Role> roles, Map<String, Integer> cinematic, List<String> stats, Map<String, String> statNames,
                       List<SkillDefinition> pickupSkills, List<Integer> btm) {
    }

    private static final List<String> MARTIAL_ARTS = List.of("aikido", "animal_kung_fu", "boxing", "capoeira", "choi_li_fut",
            "judo", "karate", "savate", "tae_kwon_do", "thai_kick_boxing", "wrestling");
    private static final List<String> OTHER_TECH = List.of("gyro_tech", "aero_tech", "weaponsmith", "electronic_security");

    /** Карьерные пакеты, с. 44: первым — спецспособность; «a|b» — выбор */
    private static final Map<String, List<String>> PACKAGES = Map.of(
            "solo", List.of("combat_sense", "awareness", "handgun", "brawling|" + String.join("|", MARTIAL_ARTS), "melee", "weaponsmith",
                    "rifle", "athletics", "submachinegun", "stealth"),
            "nomad", List.of("family", "awareness", "endurance", "melee", "rifle", "driving", "basic_tech", "wilderness_survival",
                    "brawling", "athletics"),
            "rockerboy", List.of("charismatic_leadership", "awareness", "perform", "wardrobe_style", "composition", "brawling",
                    "play_instrument", "streetwise", "persuasion", "seduction"),
            "netrunner", List.of("interface", "awareness", "basic_tech", "education", "system_knowledge", "cybertech",
                    "cyberdeck_design", "composition", "electronics", "programming"),
            "corporate", List.of("resources", "awareness", "human_perception", "education", "library_search", "social",
                    "persuasion", "stock_market", "wardrobe_style", "personal_grooming"),
            // «Any three other Tech Skills (Gyro, Aero, Weapons, Elect. Security)»
            "techie", List.of("jury_rig", "awareness", "basic_tech", "cybertech", "teaching", "education", "electronics",
                    String.join("|", OTHER_TECH), String.join("|", OTHER_TECH), String.join("|", OTHER_TECH)),
            "medtechie", List.of("medical_tech", "awareness", "basic_tech", "diagnose", "education", "cryotank", "library_search",
                    "pharmaceuticals", "zoology", "human_perception"),
            "media", List.of("credibility", "awareness", "composition", "education", "persuasion", "human_perception", "social",
                    "streetwise", "photo_film", "interview"),
            "cop", List.of("authority", "awareness", "handgun", "human_perception", "athletics", "education", "brawling", "melee",
                    "interrogation", "streetwise"),
            "fixer", List.of("streetdeal", "awareness", "forgery", "handgun", "brawling", "melee", "pick_lock", "pick_pocket",
                    "intimidate", "persuasion"));

    /** Occupation Table, с. 58: уровень спецспособности 1–5, 6, 7, 8, 9, 10 */
    private static final Map<String, List<Integer>> SALARIES = Map.of(
            "rockerboy", List.of(1000, 1500, 2000, 5000, 8000, 12000),
            "solo", List.of(2000, 3000, 4500, 7000, 9000, 12000),
            "cop", List.of(1000, 1200, 3000, 5000, 7000, 9000),
            "corporate", List.of(1500, 3000, 5000, 7000, 9000, 12000),
            "media", List.of(1000, 1200, 3000, 5000, 7000, 10000),
            "fixer", List.of(1500, 3000, 5000, 7000, 8000, 10000),
            "techie", List.of(1000, 2000, 3000, 4000, 5000, 8000),
            "netrunner", List.of(1000, 2000, 3000, 5000, 7000, 10000),
            "medtechie", List.of(1600, 3000, 5000, 7000, 10000, 15000),
            "nomad", List.of(1000, 1500, 2000, 3000, 4000, 5000));

    private final CharacterService characterService;
    private final SkillService skillService;
    private final MoneyService moneyService;
    private final DiceRoller dice;
    private final CounterRepository counterRepository;
    private final NoteService noteService;
    private final NoteCategoryService noteCategoryService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    public String getSystemCode() {
        return Cp2020System.CODE;
    }

    @Override
    public String getTemplate() {
        return "creation/cp2020";
    }

    @Override
    public Form form(Campaign campaign) {
        Map<String, String> statNames = new LinkedHashMap<>();
        STATS.forEach(stat -> statNames.put(stat, stat.toUpperCase()));
        List<SkillDefinition> pickup = Cp2020Skills.CATALOG.stream().filter(skill -> !skill.special() && !skill.custom()).toList();
        List<Integer> btm = Stream.iterate(0, body -> body + 1).limit(MAX_STAT + 1).map(Cp2020Rules::btm).toList();
        return new Form(roles(), CINEMATIC, STATS, statNames, pickup, btm);
    }

    static List<Role> roles() {
        return Cp2020Skills.ROLES.stream()
                .map(role -> new Role(role, slots(role.code()), SALARIES.get(role.code())))
                .toList();
    }

    private static List<Slot> slots(String roleCode) {
        return PACKAGES.get(roleCode).stream()
                .map(entry -> {
                    List<SkillDefinition> options = Stream.of(entry.split("\\|")).map(Cp2020Creation::skill).toList();
                    String label = options.size() == 1 ? options.get(0).name()
                            : entry.startsWith("brawling") ? "Драка или боевое искусство" : "Ещё тех-навык";
                    return new Slot(label, options);
                })
                .toList();
    }

    private static SkillDefinition skill(String code) {
        return Cp2020Skills.CATALOG.stream().filter(skill -> skill.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Нет навыка " + code));
    }

    /**
     * Случайно: 9D10 итогом; быстро: 1D10 на каждый стат, 2 и меньше перебрасываются (с. 20).
     */
    @Override
    public Roll roll(Campaign campaign, String method) {
        List<Integer> rolls = new ArrayList<>();
        switch (method) {
            case RANDOM -> {
                for (int i = 0; i < STATS_COUNT; i++) {
                    rolls.add(dice.d10());
                }
            }
            case FAST -> {
                for (int i = 0; i < STATS_COUNT; i++) {
                    int roll;
                    do {
                        roll = dice.d10();
                    } while (roll <= MIN_STAT);
                    rolls.add(roll);
                }
            }
            default -> throw new IllegalArgumentException("Такого способа нет: " + method);
        }
        return new Roll(rolls.stream().mapToInt(Integer::intValue).sum(), rolls);
    }

    /** Лайфпас, с. 34–39 */
    @Override
    public Cp2020Lifepath.Result background(Campaign campaign, Integer age) {
        return Cp2020Lifepath.generate(age, dice);
    }

    @Override
    @Transactional
    public Character create(Campaign campaign, Map<String, String> params) {
        String name = params.getOrDefault("name", "").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Как зовут персонажа?");
        }
        int points = points(params);
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (String stat : STATS) {
            int value = number(params, "stat." + stat, "Стат " + stat.toUpperCase());
            if (value < MIN_STAT || value > MAX_STAT) {
                throw new IllegalArgumentException("Стат " + stat.toUpperCase() + " — от " + MIN_STAT + " до " + MAX_STAT);
            }
            stats.put(stat, value);
        }
        int spent = stats.values().stream().mapToInt(Integer::intValue).sum();
        if (spent != points) {
            throw new IllegalArgumentException("Распределено " + spent + " очков из " + points + ": нужно ровно все (с. 20)");
        }

        Role role = roles().stream().filter(candidate -> candidate.role().code().equals(params.get("role"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Выберите роль"));
        Map<String, Integer> levels = new LinkedHashMap<>();
        int career = 0;
        for (int i = 0; i < role.slots().size(); i++) {
            Slot slot = role.slots().get(i);
            String code = slot.isChoice() ? params.getOrDefault("slot." + i, "") : slot.options().get(0).code();
            if (slot.options().stream().noneMatch(option -> option.code().equals(code))) {
                throw new IllegalArgumentException("Выберите: " + slot.label());
            }
            int level = level(params, "career." + i);
            if (levels.containsKey(code) && level > 0) {
                throw new IllegalArgumentException("Навык выбран дважды: " + skill(code).name());
            }
            career += level;
            if (level > 0) {
                levels.put(code, level);
            }
        }
        if (career > CAREER_POINTS) {
            throw new IllegalArgumentException("Карьерных очков потрачено " + career + " из " + CAREER_POINTS + " (с. 44)");
        }

        int pickupPoints = stats.get(Cp2020StatsStrategy.REF) + stats.get(Cp2020StatsStrategy.INT);
        int pickup = 0;
        Set<String> pickupCodes = new HashSet<>();
        for (int i = 0; params.containsKey("pickup." + i + ".skill"); i++) {
            String code = params.get("pickup." + i + ".skill");
            int level = level(params, "pickup." + i + ".level");
            if (code.isBlank() || level == 0) {
                continue;
            }
            SkillDefinition definition = Cp2020Skills.CATALOG.stream().filter(skill -> skill.code().equals(code) && !skill.special() && !skill.custom())
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Такого навыка хобби нет: " + code));
            if (levels.containsKey(code)) {
                // с. 45: «Pickup Skill points may not be used to increase your character's Career Skills!»
                throw new IllegalArgumentException(definition.name() + " — карьерный навык: очки хобби на него тратить нельзя");
            }
            if (!pickupCodes.add(code)) {
                throw new IllegalArgumentException("Навык хобби выбран дважды: " + definition.name());
            }
            pickup += level;
            levels.put(code, level);
        }
        if (pickup > pickupPoints) {
            throw new IllegalArgumentException("Очков хобби потрачено " + pickup + " из " + pickupPoints + " (REF + INT)");
        }

        Character character = new Character();
        character.setName(name);
        Character created = characterService.createInCampaign(campaign.getId(), character);
        Counter statsCounter = counterRepository.findByCharacterId(created.getId()).stream()
                .filter(counter -> Cp2020StatsStrategy.TYPE.equals(counter.getType()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Нет счётчика характеристик"));
        statsCounter.getAttributes().putAll(stats);
        skillService.setRoles(created.getId(), List.of(role.role().code()));
        skillService.updateLevels(created.getId(), levels, Map.of());

        int special = levels.getOrDefault(role.role().specialSkillCode(), 0);
        int salary = salary(role, special);
        // 1D6/3 вниз, но не меньше 1 (с. 20: «automatically equal to 1»)
        int months = Math.max(1, dice.roll(6) / 3);
        Money funds = new Money();
        funds.setName("Стартовые деньги: " + months + " мес. × " + salary);
        funds.setAmount((long) salary * months);
        moneyService.create(created.getId(), funds);
        applyLifepath(created, statsCounter, params);
        return created;
    }

    /**
     * Лайфпас со страницы: заметки в категории «Лайфпас», родной язык на +8, деньги и потерянные статы.
     * HOMEBREW: стат от событий не опускается ниже 1, книга о нижней границе молчит.
     */
    private void applyLifepath(Character character, Counter statsCounter, Map<String, String> params) {
        String json = params.getOrDefault("lifepath", "").strip();
        if (json.isEmpty()) {
            return;
        }
        Cp2020Lifepath.Result lifepath;
        try {
            lifepath = objectMapper.readValue(json, Cp2020Lifepath.Result.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Лайфпас не прочитать: бросьте его ещё раз");
        }
        NoteCategory category = noteCategoryService.getForCharacter(character.getId()).stream()
                .filter(existing -> LIFEPATH_CATEGORY.equals(existing.getName()))
                .findFirst()
                .orElseGet(() -> {
                    NoteCategory created = new NoteCategory();
                    created.setName(LIFEPATH_CATEGORY);
                    return noteCategoryService.createCategory(character.getId(), created);
                });
        for (Cp2020Lifepath.Section section : lifepath.sections()) {
            List<String> chunk = new ArrayList<>();
            int part = 1;
            for (String line : section.lines()) {
                if (!chunk.isEmpty() && String.join("\n", chunk).length() + line.length() + 1 > MAX_NOTE_TEXT) {
                    note(character, section.title() + " " + part++, chunk, category);
                    chunk = new ArrayList<>();
                }
                chunk.add(line.length() > MAX_NOTE_TEXT ? line.substring(0, MAX_NOTE_TEXT) : line);
            }
            note(character, part > 1 ? section.title() + " " + part : section.title(), chunk, category);
        }
        for (Cp2020Lifepath.Effect effect : lifepath.effects()) {
            if (effect.stat() != null && STATS.contains(effect.stat())) {
                int before = statsCounter.getAttribute(effect.stat(), Cp2020StatsStrategy.DEFAULT_STAT);
                statsCounter.getAttributes().put(effect.stat(), Math.max(1, before + effect.delta()));
            }
            if (effect.money() != 0) {
                Money money = new Money();
                money.setName("Лайфпас: " + effect.label());
                money.setAmount(effect.money());
                moneyService.create(character.getId(), money);
            }
        }
        String language = params.getOrDefault("nativeLanguage", "");
        lifepath.languages().stream()
                .filter(candidate -> (candidate.name() + "|" + candidate.family()).equals(language))
                .findFirst()
                .ifPresent(native_ -> skillService.addCustom(character.getId(), "language_" + native_.family(),
                        capitalize(native_.name()), NATIVE_LANGUAGE));
    }

    private void note(Character character, String title, List<String> lines, NoteCategory category) {
        if (lines.isEmpty()) {
            return;
        }
        Note note = new Note();
        note.setCharacter(character);
        note.setName(title);
        note.setText(String.join("\n", lines));
        note.getCategories().add(category);
        noteService.createNote(note);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : java.lang.Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static int salary(Role role, int specialLevel) {
        int index = Math.max(0, Math.min(specialLevel, MAX_LEVEL) - 5);
        return role.salaries().get(index);
    }

    private static int points(Map<String, String> params) {
        String method = params.getOrDefault("method", "");
        int points = number(params, "points", "Очки характеристик");
        if (CINEMATIC.containsKey(method) && CINEMATIC.get(method) != points) {
            throw new IllegalArgumentException(method + " — это " + CINEMATIC.get(method) + " очков");
        }
        if (!CINEMATIC.containsKey(method) && !Set.of(RANDOM, FAST, MANUAL).contains(method)) {
            throw new IllegalArgumentException("Выберите способ определить очки");
        }
        if (points < MIN_STAT * STATS_COUNT || points > MAX_STAT * STATS_COUNT) {
            throw new IllegalArgumentException("Очков — от " + MIN_STAT * STATS_COUNT + " до " + MAX_STAT * STATS_COUNT);
        }
        return points;
    }

    private static int level(Map<String, String> params, String key) {
        String raw = params.getOrDefault(key, "").strip();
        if (raw.isEmpty()) {
            return 0;
        }
        int level = number(params, key, "Уровень навыка");
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Уровень навыка — от 0 до " + MAX_LEVEL);
        }
        return level;
    }

    private static int number(Map<String, String> params, String key, String label) {
        try {
            return Integer.parseInt(params.getOrDefault(key, "").strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + ": нужно число");
        }
    }
}
