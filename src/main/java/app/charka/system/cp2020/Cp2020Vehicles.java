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

/**
 * Транспорт (с. 113): контроль, урон в SDP через SP, столкновение и таран. Характеристик моделей в книге нет —
 * SDP, SP и безопасную скорость вводит мастер; цены справочника — с. 71.
 */
public final class Cp2020Vehicles {

    public static final String TYPE = "CP2020_VEHICLE";
    public static final String KIND = "kind";
    public static final String SDP = "sdp";
    public static final String SP = "sp";
    public static final String SPEED = "speed";
    /** Сколько SDP потеряно; состояние, не поле формы */
    public static final String DAMAGE = "sdpLost";

    public static final String CONTROL = "CONTROL";
    public static final String HIT = "DAMAGE";
    public static final String RAM = "RAM";
    public static final String REPAIR = "REPAIR";

    /**
     * Вид машины: модификатор контроля (с. 113) и навык. HOMEBREW: книга называет только Driving и Pilot;
     * мотоцикл водят навыком Motorcycle, AV и Osprey — Pilot: Vector Thrust, винтокрыл — Pilot: Gyro, лодку — Driving.
     */
    enum Kind {
        CAR("Машина", 0, "driving", false),
        LIMOUSINE("Лимузин", -3, "driving", false),
        SPORTSCAR("Спорткар", 2, "driving", false),
        MOTORCYCLE("Мотоцикл", 1, "motorcycle", false),
        TRUCK("Грузовик", -4, "driving", false),
        BOAT("Лодка", -1, "driving", false),
        AV4("AV-4", -2, "pilot_vector_thrust", true),
        AV6("AV-6", 2, "pilot_vector_thrust", true),
        AV7("AV-7", 1, "pilot_vector_thrust", true),
        ROTORCRAFT("Винтокрыл", 0, "pilot_gyro", true),
        OSPREY("Osprey", 0, "pilot_vector_thrust", true);

        final String label;
        final int control;
        final String skill;
        final boolean aircraft;

        Kind(String label, int control, String skill, boolean aircraft) {
            this.label = label;
            this.control = control;
            this.skill = skill;
            this.aircraft = aircraft;
        }
    }

    /** Масса того, во что врезались: множитель кубиков (с. 113). Множитель ×2, чтобы ×0.5 было целым */
    enum Mass {
        VERY_LIGHT("Очень лёгкое: коробка", 1),
        LIGHT("Лёгкое: человек", 2),
        MEDIUM("Среднее: мотоцикл", 4),
        HEAVY("Тяжёлое: машина", 6),
        VERY_HEAVY("Очень тяжёлое: грузовик, земля", 8);

        final String label;
        final int doubled;

        Mass(String label, int doubled) {
            this.label = label;
            this.doubled = doubled;
        }
    }

    private static final List<Option> DIFFICULTIES = List.of(
            new Option("15", "Простой манёвр: 15"),
            new Option("20", "Сложный: 20"),
            new Option("25", "Очень сложный: 25"));

    public static final ItemTypeDefinition VEHICLE_TYPE = new ItemTypeDefinition(TYPE, "Транспорт", List.of(
            ItemField.choice(KIND, "Вид", true, Arrays.stream(Kind.values()).map(kind -> new Option(kind.name(), kind.label)).toArray(Option[]::new)),
            ItemField.number(SDP, "SDP", "SDP ", "", 1, null),
            ItemField.number(SP, "SP", "SP ", "", 0, null),
            ItemField.number(SPEED, "Безопасная скорость", "", " км/ч", 1, null)),
            values -> {
            },
            List.of(DAMAGE)).inSection("Транспорт");

    private static final String GROUP = "Транспорт";

    /** Цены с. 71; SDP и SP в книге нет */
    public static final List<CatalogItem> CATALOG = List.of(
            vehicle("scooter", "Скутер", Kind.MOTORCYCLE, 500),
            vehicle("motorcycle", "Мотоцикл", Kind.MOTORCYCLE, 1500),
            vehicle("citycar", "CityCar", Kind.CAR, 2000),
            vehicle("subcompact", "Малолитражка", Kind.CAR, 6000),
            vehicle("sedan", "Седан", Kind.CAR, 10000),
            vehicle("sportscar", "Спорткар", Kind.SPORTSCAR, 20000),
            vehicle("luxury", "Люксовая машина", Kind.CAR, 40000));

    private Cp2020Vehicles() {
    }

    private static CatalogItem vehicle(String key, String name, Kind kind, int cost) {
        return new CatalogItem("vehicle-" + key, GROUP, name, TYPE, cost,
                "SDP и SP в основной книге нет — впишите в ✎", Map.of(KIND, kind.name()), 1, true);
    }

    public static boolean isVehicle(Item item) {
        return TYPE.equals(item.getType());
    }

    static Kind kind(Item item) {
        try {
            return Kind.valueOf(item.getProperties().getOrDefault(KIND, Kind.CAR.name()));
        } catch (IllegalArgumentException e) {
            return Kind.CAR;
        }
    }

    static Optional<Integer> sdp(Item item) {
        return number(item, SDP);
    }

    static int damage(Item item) {
        return number(item, DAMAGE).orElse(0);
    }

    private static Optional<Integer> number(Item item, String key) {
        String value = item.getProperties().get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(Integer.parseInt(value));
    }

    public static ItemSummary summary(Item item) {
        List<String> lines = new ArrayList<>();
        lines.add(kind(item).label);
        number(item, SP).filter(sp -> sp > 0).ifPresent(sp -> lines.add("SP " + sp));
        number(item, SPEED).ifPresent(speed -> lines.add(speed + " км/ч"));
        String main = sdp(item).map(sdp -> "SDP " + Math.max(0, sdp - damage(item)) + "/" + sdp).orElse(null);
        return new ItemSummary(main, lines);
    }

    public static List<String> notes(Item item) {
        return sdp(item).filter(sdp -> damage(item) >= sdp).map(sdp -> List.of("Разрушена или не на ходу")).orElse(List.of());
    }

    public static List<ItemAction> actions(Item item) {
        String noSdp = sdp(item).isEmpty() ? "Впишите SDP машины в ✎" : null;
        List<Option> masses = Arrays.stream(Mass.values()).map(mass -> new Option(mass.name(), mass.label)).toList();
        int speed = number(item, SPEED).orElse(60);
        List<ItemAction> actions = new ArrayList<>();
        actions.add(ItemAction.withSourcesAndAmount(CONTROL, "🛞 Контроль", DIFFICULTIES, 1, 4)
                .withLabels("Манёвр", "Во сколько раз быстрее безопасной скорости").forCombat());
        actions.add(ItemAction.withAmount(HIT, "💥 Урон машине", noSdp, 10, 999).withLabels(null, "Урон").asSecondary());
        actions.add(ItemAction.withSourcesAndAmount(RAM, "Столкновение", masses, speed, 2000).withLabels("Во что врезались", "Скорость").asSecondary());
        actions.add(ItemAction.simple(REPAIR, "🔧 Починить", damage(item) == 0 ? "Машина цела" : null).asSecondary());
        return actions;
    }

    /**
     * @param params {@code source} — сложность или масса, {@code amount} — кратность скорости, урон или скорость
     */
    static String perform(Item item, String action, Map<String, String> params, Map<String, StatValue> stats,
                          CheckResolver resolver, DiceRoller dice) {
        return switch (action) {
            case CONTROL -> control(item, params, stats, resolver, dice);
            case HIT -> hit(item, amount(params, 0));
            case RAM -> ram(item, params, dice);
            case REPAIR -> {
                item.getProperties().remove(DAMAGE);
                yield item.getName() + " починена";
            }
            default -> throw new IllegalArgumentException("Неизвестное действие с транспортом: " + action);
        };
    }

    /** REF + навык + 1D10 + вид машины + скорость ≥ сложность; провал — 1D6 по таблице потери контроля */
    private static String control(Item item, Map<String, String> params, Map<String, StatValue> stats, CheckResolver resolver, DiceRoller dice) {
        Kind kind = kind(item);
        int difficulty = DIFFICULTIES.stream().map(Option::value).filter(value -> value.equals(params.get("source")))
                .findFirst().map(Integer::parseInt).orElse(15);
        int speedTimes = Math.min(4, Math.max(1, amount(params, 1)));
        Character driver = item.getInventory().getCharacter();
        StatValue ref = stats.get(Cp2020StatsStrategy.REF);
        String skillName = Cp2020Skills.CATALOG.stream().filter(definition -> definition.code().equals(kind.skill))
                .findFirst().map(definition -> definition.name()).orElse(kind.skill);
        List<CheckRequest.Part> parts = new ArrayList<>();
        parts.add(new CheckRequest.Part("REF", ref == null ? 0 : ref.value()));
        parts.add(new CheckRequest.Part(skillName, skillLevel(driver, kind.skill)));
        if (kind.control != 0) {
            parts.add(new CheckRequest.Part(kind.label, kind.control));
        }
        if (speedTimes > 1) {
            parts.add(new CheckRequest.Part("Скорость ×" + speedTimes, -2 * (speedTimes - 1)));
        }
        CheckResult result = resolver.resolve(CheckRequest.ofParts("Контроль: " + item.getName(), parts, difficulty, kind.skill), dice);
        String text = "Контроль " + result.total() + " против " + difficulty;
        if (Boolean.TRUE.equals(result.success())) {
            return text + ": удержал";
        }
        return text + ": " + controlLoss(item, kind, dice);
    }

    private static String controlLoss(Item item, Kind kind, DiceRoller dice) {
        int roll = dice.roll(6);
        if (roll <= 2) {
            return "занос (1D6 = " + roll + ")";
        }
        int tens = dice.roll(10);
        if (roll <= 4) {
            return kind.aircraft
                    ? "сваливание, потеря высоты " + tens * 50 + " футов (1D6 = " + roll + ")"
                    : "сильный занос, снос на " + tens * 10 + " футов вбок (1D6 = " + roll + ")";
        }
        if (kind.aircraft) {
            return "штопор, потеря высоты " + tens * 100 + " футов (1D6 = " + roll + ")";
        }
        int damage = 0;
        for (int i = 0; i < 5; i++) {
            damage += dice.roll(6);
        }
        return "переворот после сноса на " + tens * 10 + " футов (1D6 = " + roll + "); " + hit(item, damage);
    }

    /** Урон − SP, остаток в SDP; 0 SDP — разрушена */
    static String hit(Item item, int damage) {
        int sdp = sdp(item).orElseThrow(() -> new IllegalArgumentException("Впишите SDP машины"));
        int sp = number(item, SP).orElse(0);
        int through = Math.max(0, damage - sp);
        int lost = Math.min(sdp, damage(item) + through);
        item.getProperties().put(DAMAGE, String.valueOf(lost));
        String text = item.getName() + ": урон " + damage + (sp > 0 ? " − SP " + sp : "") + " = " + through
                + ", SDP " + (sdp - lost) + "/" + sdp;
        return lost >= sdp ? text + " — разрушена" : text;
    }

    /** Скорость / 20 кубиков D6 × масса; пассажирам — половина кубиков */
    private static String ram(Item item, Map<String, String> params, DiceRoller dice) {
        Mass mass = Arrays.stream(Mass.values()).filter(value -> value.name().equals(params.get("source"))).findFirst().orElse(Mass.HEAVY);
        int speed = amount(params, 0);
        int count = speed / 20;
        if (count < 1) {
            return "На скорости " + speed + " урона нет: нужно от 20";
        }
        int rolled = 0;
        for (int i = 0; i < count; i++) {
            rolled += dice.roll(6);
        }
        int damage = rolled * mass.doubled / 2;
        int passengers = damage / 2;
        String text = "Столкновение: " + count + "D6 = " + rolled + " × " + (mass.doubled % 2 == 0 ? String.valueOf(mass.doubled / 2) : "0.5")
                + " = " + damage + "; пассажирам по " + passengers;
        return sdp(item).isPresent() ? text + ". " + hit(item, damage) : text;
    }

    private static int amount(Map<String, String> params, int fallback) {
        String value = params.get("amount");
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Нужно число");
        }
    }

    private static int skillLevel(Character character, String code) {
        int natural = character.getSkills().stream()
                .filter(skill -> code.equals(skill.getSkillCode()))
                .mapToInt(CharacterSkill::getLevel)
                .findFirst()
                .orElse(0);
        return Cp2020Chips.skillLevel(character, code, natural);
    }
}
