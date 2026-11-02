package app.charka.system.cp2020;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.check.CheckResult;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemField;
import app.charka.item.ItemField.Option;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.StatValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Нетраннинг, учёт (с. 133–143): кибердека (скорость, память, Data Wall) и программы (класс, сила, MU).
 * Программа загружается в деку, пока хватает памяти; «Запустить» бросает силу программы по правилу её класса.
 * Сетевая карта, ICE крепостей и ход в Сети не ведутся.
 */
public final class Cp2020Netrunning {

    public static final String DECK = "CP2020_DECK";
    public static final String PROGRAM = "CP2020_PROGRAM";

    public static final String SPEED = "speed";
    public static final String MEMORY = "mu";
    public static final String DATA_WALL = "dataWall";
    public static final String CLASS = "programClass";
    public static final String STRENGTH = "strength";
    /** id деки, в которую загружена программа; состояние */
    public static final String LOADED_IN = "loadedIn";

    public static final String LOAD = "LOAD";
    public static final String UNLOAD = "UNLOAD";
    public static final String RUN = "RUN";

    private static final String SECTION = "Сеть";
    private static final String INTERFACE = "interface";

    /** Класс программы (с. 137): как бросать её силу */
    enum ProgramClass {
        INTRUSION("Взлом", Roll.STRENGTH),
        DECRYPTION("Расшифровка", Roll.STRENGTH),
        DETECTION("Обнаружение", Roll.STRENGTH),
        ANTI_SYSTEM("Против систем", Roll.STRENGTH),
        EVASION("Скрытность", Roll.STRENGTH),
        PROTECTION("Защита", Roll.STRENGTH),
        ANTI_IC("Против ICE", Roll.STRENGTH),
        ANTI_PERSONNEL("Против нетраннера", Roll.COMBAT),
        CONTROLLER("Управление", Roll.UTILITY),
        UTILITY("Утилита", Roll.UTILITY),
        DEMON("Демон", Roll.STRENGTH);

        final String label;
        final Roll roll;

        ProgramClass(String label, Roll roll) {
            this.label = label;
            this.roll = roll;
        }
    }

    /**
     * С. 144–153 (выжимка в RULES.md): обычная программа — сила + 1D10 против защиты; бой против нетраннера —
     * сила + INT + Interface + 1D10 с обеих сторон; утилита срабатывает на 1D10 ≤ силы.
     */
    enum Roll {
        STRENGTH, COMBAT, UTILITY
    }

    public static final ItemTypeDefinition DECK_TYPE = new ItemTypeDefinition(DECK, "Кибердека", List.of(
            ItemField.number(SPEED, "Скорость", "скорость ", "", -5, 5),
            ItemField.number(MEMORY, "Память, MU", "", " MU", 1, 100),
            ItemField.number(DATA_WALL, "Data Wall", "стена ", "", 0, 10)),
            Cp2020Netrunning::checkDeck).inSection(SECTION);

    public static final ItemTypeDefinition PROGRAM_TYPE = new ItemTypeDefinition(PROGRAM, "Программа", List.of(
            ItemField.choice(CLASS, "Класс", true, Arrays.stream(ProgramClass.values())
                    .map(value -> new Option(value.name(), value.label)).toArray(Option[]::new)),
            ItemField.number(STRENGTH, "Сила", "сила ", "", 0, 20),
            ItemField.number(MEMORY, "MU", "", " MU", 0, 20)),
            values -> {
            },
            List.of(LOADED_IN)).inSection(SECTION);

    private record Program(String name, ProgramClass programClass, int strength, int mu, int cost, String note) {
    }

    /** Таблица с. 137–140 (RULES.md, «Программы») */
    private static final List<Program> PROGRAMS = List.of(
            new Program("Hammer", ProgramClass.INTRUSION, 4, 1, 400, "Бьёт стену: −1D6 к силе стены"),
            new Program("Jackhammer", ProgramClass.INTRUSION, 2, 2, 360, "−1D6 к силе стены после атаки"),
            new Program("Worm", ProgramClass.INTRUSION, 2, 5, 660, null),
            new Program("Codecracker", ProgramClass.DECRYPTION, 3, 2, 380, null),
            new Program("Wizard's Book", ProgramClass.DECRYPTION, 4, 2, 400, "Файлы и кодовые ворота"),
            new Program("Raffles", ProgramClass.DECRYPTION, 5, 3, 560, "Файлы и кодовые ворота"),
            new Program("Watchdog", ProgramClass.DETECTION, 4, 5, 610, "Сообщает владельцу о входе"),
            new Program("Bloodhound", ProgramClass.DETECTION, 3, 5, 700, "Находит вход и ведёт след"),
            new Program("Pit Bull", ProgramClass.DETECTION, 2, 6, 780, "Находит, ведёт след, рвёт линию"),
            new Program("SeeYa", ProgramClass.DETECTION, 3, 1, 280, "Видит невидимые иконы"),
            new Program("Hidden Virtue", ProgramClass.DETECTION, 3, 1, 280, "Отличает настоящее от виртуального"),
            new Program("Speedtrap", ProgramClass.DETECTION, 4, 4, 600, "Скрытое в 10 клетках"),
            new Program("Flatline", ProgramClass.ANTI_SYSTEM, 3, 2, 570, "Убивает CPU"),
            new Program("Poison Flatline", ProgramClass.ANTI_SYSTEM, 2, 2, 540, "Стирает память системы"),
            new Program("Krash", ProgramClass.ANTI_SYSTEM, 3, 2, 570, "CPU выключен на 1D6+1 раунд"),
            new Program("DecKRASH", ProgramClass.ANTI_SYSTEM, 4, 2, 600, null),
            new Program("Murphy", ProgramClass.ANTI_SYSTEM, 3, 2, 600, null),
            new Program("Viral 15", ProgramClass.ANTI_SYSTEM, 4, 2, 590, "Стирает по файлу в раунд"),
            new Program("Virizz", ProgramClass.ANTI_SYSTEM, 4, 2, 600, "Занимает действие системы"),
            new Program("Invisibility", ProgramClass.EVASION, 3, 1, 300, null),
            new Program("Stealth", ProgramClass.EVASION, 4, 3, 480, null),
            new Program("Replicator", ProgramClass.EVASION, 3, 2, 320, "4 против «псов»"),
            new Program("Shield", ProgramClass.PROTECTION, 3, 1, 150, null),
            new Program("Force Shield", ProgramClass.PROTECTION, 4, 2, 160, null),
            new Program("Reflector", ProgramClass.PROTECTION, 5, 2, 160, "Stun, Hellbolt, Knockout"),
            new Program("Armor", ProgramClass.PROTECTION, 4, 2, 170, "−3 к урону антиперсональных"),
            new Program("Flak", ProgramClass.PROTECTION, 4, 2, 180, "2 против «псов»"),
            new Program("Killer II", ProgramClass.ANTI_IC, 2, 5, 1320, "1D6 к силе цели"),
            new Program("Killer IV", ProgramClass.ANTI_IC, 4, 5, 1400, "1D6 к силе цели"),
            new Program("Killer VI", ProgramClass.ANTI_IC, 6, 5, 1480, "1D6 к силе цели"),
            new Program("Manticore", ProgramClass.ANTI_IC, 2, 3, 880, "Против демонов"),
            new Program("Hydra", ProgramClass.ANTI_IC, 3, 3, 920, "Против демонов"),
            new Program("Dragon", ProgramClass.ANTI_IC, 4, 3, 960, "Против демонов"),
            new Program("Aardvark", ProgramClass.ANTI_IC, 4, 3, 1000, "Против червей"),
            new Program("Stun", ProgramClass.ANTI_PERSONNEL, 3, 3, 6000, "Замирает на 1D6 раундов"),
            new Program("Hellbolt", ProgramClass.ANTI_PERSONNEL, 4, 4, 6750, "1D10 урона"),
            new Program("Sword", ProgramClass.ANTI_PERSONNEL, 3, 4, 6250, "1D6 урона"),
            new Program("Brainwipe", ProgramClass.ANTI_PERSONNEL, 3, 4, 6500, "−1D6 INT в раунд"),
            new Program("Zombie", ProgramClass.ANTI_PERSONNEL, 5, 4, 7500, "−1D6 INT в раунд"),
            new Program("Liche", ProgramClass.ANTI_PERSONNEL, 4, 4, 7250, "Стирает память"),
            new Program("Firestarter", ProgramClass.ANTI_PERSONNEL, 4, 4, 6250, "Поджигает деку"),
            new Program("Hellhound", ProgramClass.ANTI_PERSONNEL, 6, 6, 10000, "2D10 урона"),
            new Program("Spazz", ProgramClass.ANTI_PERSONNEL, 4, 3, 6250, "REF вдвое на 1D6 раундов"),
            new Program("Glue", ProgramClass.ANTI_PERSONNEL, 5, 4, 6500, "Держит 1D10 раундов"),
            new Program("Knockout", ProgramClass.ANTI_PERSONNEL, 4, 3, 6250, "Кома на 1D6 часов"),
            new Program("Jack Attack", ProgramClass.ANTI_PERSONNEL, 3, 3, 6000, "Не даёт выйти из Сети"),
            new Program("Viddy Master", ProgramClass.CONTROLLER, 4, 1, 140, "Видеоборды"),
            new Program("Soundmachine", ProgramClass.CONTROLLER, 4, 1, 140, "Микрофоны, динамики"),
            new Program("Open Sesame", ProgramClass.CONTROLLER, 3, 1, 130, "Двери, лифты"),
            new Program("Genie", ProgramClass.CONTROLLER, 5, 1, 150, "Двери, лифты"),
            new Program("Hotwire", ProgramClass.CONTROLLER, 3, 1, 130, "Машины, роботы"),
            new Program("Dee-2", ProgramClass.CONTROLLER, 3, 1, 130, null),
            new Program("Crystal Ball", ProgramClass.CONTROLLER, 4, 1, 140, "Камеры, датчики"),
            new Program("News at 8", ProgramClass.CONTROLLER, 4, 1, 140, null),
            new Program("Phone Home", ProgramClass.CONTROLLER, 5, 1, 150, "Звонки из Сети"),
            new Program("Databaser", ProgramClass.UTILITY, 8, 2, 180, null),
            new Program("Alias", ProgramClass.UTILITY, 6, 2, 160, null),
            new Program("Re-Rezz", ProgramClass.UTILITY, 3, 1, 130, "Чинит файлы и программы"),
            new Program("Instant Replay", ProgramClass.UTILITY, 8, 2, 180, null),
            new Program("GateMaster", ProgramClass.UTILITY, 5, 1, 150, "Убивает Virizz и Viral 15"),
            new Program("Padlock", ProgramClass.UTILITY, 4, 2, 160, "Запирает деку"),
            new Program("ElectroLock", ProgramClass.UTILITY, 7, 2, 170, "Файл как ворота силы 3"),
            new Program("Filelocker", ProgramClass.UTILITY, 4, 1, 140, "Файл как ворота силы 5"),
            new Program("NetMap", ProgramClass.UTILITY, 4, 1, 150, null),
            new Program("File Packer", ProgramClass.UTILITY, 4, 1, 140, "Сжимает файлы вдвое"),
            new Program("Backup", ProgramClass.UTILITY, 4, 1, 140, null),
            new Program("Imp", ProgramClass.DEMON, 3, 3, 1000, "Носит 2 программы"),
            new Program("Afreet", ProgramClass.DEMON, 3, 4, 1160, "Носит 3 программы"),
            new Program("Succubus", ProgramClass.DEMON, 4, 4, 1200, "Носит 4 программы"),
            new Program("Balron", ProgramClass.DEMON, 5, 5, 1240, "Носит 4 программы"));

    /** Деки с. 133–134: стандартная 10 MU, скорость 0, стена 2 */
    public static final List<CatalogItem> CATALOG = Stream.concat(Stream.of(
                    deck("deck-used", "Кибердека б/у", 500, 0, 10, 2, "Стандартная, подержанная"),
                    deck("deck-standard", "Кибердека", 1000, 0, 10, 2, "Стандартная, новая"),
                    deck("deck-portable", "Кибердека переносная", 2000, 0, 10, 2, "Батарея на 4 часа"),
                    deck("deck-combat", "Кибердека боевая", 3000, 0, 10, 2, "Корпус SP 20, около 3000"),
                    deck("deck-cellular", "Кибердека сотовая", 4000, 0, 10, 2, "В движущейся машине 25% потерять связь"),
                    deck("deck-zetatech", "Zetatech Parraline 5750", 3600, -2, 10, 4, "Около 3600"),
                    deck("deck-elysia", "«Elysia»", 4260, 3, 20, 5, null)),
            PROGRAMS.stream().map(program -> new CatalogItem("program-" + program.name().toLowerCase().replaceAll("[^a-z0-9]+", "-"),
                    "Программы: " + program.programClass().label, program.name(), PROGRAM, program.cost(), program.note(),
                    Map.of(CLASS, program.programClass().name(), STRENGTH, String.valueOf(program.strength()),
                            MEMORY, String.valueOf(program.mu())), 1, true)))
            .toList();

    private Cp2020Netrunning() {
    }

    private static CatalogItem deck(String key, String name, int cost, int speed, int memory, int wall, String note) {
        return new CatalogItem(key, "Кибердеки", name, DECK, cost, note,
                Map.of(SPEED, String.valueOf(speed), MEMORY, String.valueOf(memory), DATA_WALL, String.valueOf(wall)), 1, true);
    }

    /** С. 134: скорость не выше 5, стена не выше 10 */
    private static void checkDeck(Map<String, String> values) {
        if (!values.containsKey(MEMORY)) {
            throw new IllegalArgumentException("Поле «Память, MU» обязательно");
        }
    }

    public static boolean isDeck(Item item) {
        return DECK.equals(item.getType());
    }

    public static boolean isProgram(Item item) {
        return PROGRAM.equals(item.getType());
    }

    private static int number(Item item, String key, int fallback) {
        String value = item.getProperties().get(key);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private static Stream<Item> items(Character character) {
        return character.getInventories().stream().flatMap(inventory -> inventory.getContent().stream());
    }

    /** Программы, загруженные в деку */
    static List<Item> loaded(Item deck) {
        String id = String.valueOf(deck.getId());
        return items(deck.getInventory().getCharacter())
                .filter(Cp2020Netrunning::isProgram)
                .filter(program -> id.equals(program.getProperties().get(LOADED_IN)))
                .toList();
    }

    static int usedMemory(Item deck) {
        return loaded(deck).stream().mapToInt(program -> number(program, MEMORY, 0)).sum();
    }

    private static Optional<Item> deckOf(Item program) {
        String id = program.getProperties().get(LOADED_IN);
        return id == null ? Optional.empty() : items(program.getInventory().getCharacter())
                .filter(Cp2020Netrunning::isDeck)
                .filter(deck -> id.equals(String.valueOf(deck.getId())))
                .findFirst();
    }

    public static ItemSummary summary(Item item) {
        if (isDeck(item)) {
            int speed = number(item, SPEED, 0);
            return new ItemSummary("MU " + usedMemory(item) + "/" + number(item, MEMORY, 10),
                    List.of("скорость " + (speed > 0 ? "+" : "") + speed, "стена " + number(item, DATA_WALL, 2)));
        }
        ProgramClass programClass = programClass(item);
        List<String> lines = new ArrayList<>();
        lines.add(programClass.label);
        lines.add(number(item, MEMORY, 0) + " MU");
        deckOf(item).ifPresent(deck -> lines.add("в «" + deck.getName() + "»"));
        return new ItemSummary("сила " + number(item, STRENGTH, 0), lines);
    }

    public static List<String> notes(Item item) {
        if (isDeck(item)) {
            List<Item> programs = loaded(item);
            return programs.isEmpty() ? List.of("Программ не загружено")
                    : List.of("Загружено: " + String.join(", ", programs.stream().map(Item::getName).toList()));
        }
        return List.of();
    }

    public static List<ItemAction> actions(Item item) {
        if (isDeck(item)) {
            return List.of();
        }
        List<ItemAction> actions = new ArrayList<>();
        Optional<Item> deck = deckOf(item);
        if (deck.isPresent()) {
            actions.add(ItemAction.simple(RUN, "▶ Запустить", null).forCombat());
            actions.add(ItemAction.simple(UNLOAD, "Выгрузить", null).asSecondary());
        } else {
            int mu = number(item, MEMORY, 0);
            List<Option> decks = items(item.getInventory().getCharacter())
                    .filter(Cp2020Netrunning::isDeck)
                    .filter(candidate -> usedMemory(candidate) + mu <= number(candidate, MEMORY, 10))
                    .map(candidate -> new Option(String.valueOf(candidate.getId()), candidate.getName()))
                    .toList();
            String unavailable = decks.isEmpty() ? "Нет деки со свободными " + mu + " MU" : null;
            actions.add(decks.size() == 1
                    ? ItemAction.simple(LOAD, "⇪ В деку", unavailable)
                    : ItemAction.withSources(LOAD, "⇪ В деку", unavailable, decks).withLabels("В какую деку", null));
        }
        return actions;
    }

    static String perform(Item item, String action, Map<String, String> params, Map<String, StatValue> stats,
                          CheckResolver resolver, DiceRoller dice) {
        if (!isProgram(item)) {
            throw new IllegalArgumentException("У деки нет действий: загрузите в неё программу");
        }
        return switch (action) {
            case LOAD -> load(item, params.get("source"));
            case UNLOAD -> {
                item.getProperties().remove(LOADED_IN);
                yield item.getName() + " выгружена";
            }
            case RUN -> run(item, stats, resolver, dice);
            default -> throw new IllegalArgumentException("Неизвестное действие с программой: " + action);
        };
    }

    private static String load(Item program, String deckId) {
        int mu = number(program, MEMORY, 0);
        List<Item> decks = items(program.getInventory().getCharacter()).filter(Cp2020Netrunning::isDeck).toList();
        Item deck = deckId == null || deckId.isBlank()
                ? decks.stream().filter(candidate -> usedMemory(candidate) + mu <= number(candidate, MEMORY, 10)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет деки со свободными " + mu + " MU"))
                : decks.stream().filter(candidate -> deckId.equals(String.valueOf(candidate.getId()))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Такой деки у персонажа нет"));
        int free = number(deck, MEMORY, 10) - usedMemory(deck);
        if (mu > free) {
            throw new IllegalArgumentException("В «" + deck.getName() + "» свободно " + free + " MU, а нужно " + mu);
        }
        program.getProperties().put(LOADED_IN, String.valueOf(deck.getId()));
        return program.getName() + " в «" + deck.getName() + "»: MU " + (number(deck, MEMORY, 10) - free + mu) + "/" + number(deck, MEMORY, 10);
    }

    private static String run(Item program, Map<String, StatValue> stats, CheckResolver resolver, DiceRoller dice) {
        int strength = number(program, STRENGTH, 0);
        ProgramClass programClass = programClass(program);
        if (programClass.roll == Roll.UTILITY) {
            int roll = dice.roll(10);
            return program.getName() + ": 1D10 = " + roll + (roll <= strength ? " ≤ " : " > ") + "силы " + strength
                    + (roll <= strength ? " — сработала" : " — не сработала");
        }
        List<CheckRequest.Part> parts = new ArrayList<>();
        parts.add(new CheckRequest.Part("Сила", strength));
        if (programClass.roll == Roll.COMBAT) {
            Character runner = program.getInventory().getCharacter();
            StatValue intelligence = stats.get(Cp2020StatsStrategy.INT);
            parts.add(new CheckRequest.Part("INT", intelligence == null ? 0 : intelligence.value()));
            parts.add(new CheckRequest.Part("Интерфейс", skillLevel(runner)));
        }
        CheckResult result = resolver.resolve(CheckRequest.ofParts(program.getName(), parts, null, null), dice);
        return program.getName() + ": " + result.total() + " (" + String.join(", ", result.steps()) + ")"
                + (programClass.roll == Roll.COMBAT ? " против того же броска цели" : " против защиты цели + 1D10");
    }

    private static ProgramClass programClass(Item item) {
        try {
            return ProgramClass.valueOf(item.getProperties().getOrDefault(CLASS, ProgramClass.UTILITY.name()));
        } catch (IllegalArgumentException e) {
            return ProgramClass.UTILITY;
        }
    }

    private static int skillLevel(Character character) {
        int natural = character.getSkills().stream()
                .filter(skill -> INTERFACE.equals(skill.getSkillCode()))
                .mapToInt(CharacterSkill::getLevel)
                .findFirst()
                .orElse(0);
        return Cp2020Chips.skillLevel(character, INTERFACE, natural);
    }
}
