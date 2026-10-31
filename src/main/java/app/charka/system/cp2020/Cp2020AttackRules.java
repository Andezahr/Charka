package app.charka.system.cp2020;

import app.charka.attack.AreaDamage;
import app.charka.attack.AttackForm;
import app.charka.attack.AttackContext;
import app.charka.attack.AttackHit;
import app.charka.attack.AttackOutcome;
import app.charka.attack.AttackPlan;
import app.charka.attack.AttackRequest;
import app.charka.attack.AttackRules;
import app.charka.attack.AttackSource;
import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.dice.DiceExpression;
import app.charka.item.ItemField.Option;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.StatValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Дальняя атака (FNFF, с. 99, 105–106): REF + навык оружия + WA + модификаторы + 1d10 против сложности по дальности.
 * Попадание: зона 1d10 или выбранная, урон оружия. Режимы огня: одиночный, очередь, автоогонь, подавляющий. Ближний бой — C-06.
 * Метание (с. 99, 108, 109; навык Athletics) и урон по площади (с. 99, 108–111): задет всякий в зоне, при промахе заряд отклоняется по таблице гранат.
 */
final class Cp2020AttackRules implements AttackRules {

    static final int POINT_BLANK = 10;
    static final int CLOSE = 15;
    static final int MEDIUM = 20;
    static final int LONG = 25;
    static final int EXTREME = 30;

    static final String SINGLE = "SINGLE";
    static final String BURST = "BURST";
    static final String AUTO = "AUTO";
    static final String SUPPRESS = "SUPPRESS";
    static final String PATTERNS = "PATTERNS";
    static final String THROW = "THROW";
    static final String PLANT = "PLANT";

    private static final int POINT_BLANK_METERS = 1;
    private static final int BURST_ROUNDS = 3;
    private static final int BURST_BONUS = 3;
    private static final int ROUNDS_PER_STEP = 10;
    private static final String ATHLETICS = "athletics";
    private static final int GRENADE_METERS_PER_BODY = 10;
    private static final int THROWN_METERS_PER_BODY = 3;
    private static final int METERS_LOST_PER_EXTRA_KG = 10;
    private static final int EXTRA_PATTERN_PENALTY = -2;

    /** Урон с делением: «1D6/3» */
    private static final Pattern DAMAGE = Pattern.compile("^(\\d*[dD]\\d+(?:[+-]\\d+)?)(?:/(\\d+))?$");

    private static final List<String> EXOTIC_SKILLS = List.of("archery", "handgun", "submachinegun", "rifle", "heavy_weapons");

    @Override
    public AttackForm form(AttackSource source, Map<String, StatValue> stats) {
        return source.item() == null ? null : form(source.item(), stats);
    }

    @Override
    public AttackPlan plan(AttackContext context, DiceRoller dice) {
        Item weapon = context.item();
        AttackRequest request = context.request();
        List<Character> targets = context.targets();
        if (isZoneFire(request)) {
            List<AttackPlan.PlannedCheck> saves = new ArrayList<>();
            for (Character target : targets) {
                saves.add(new AttackPlan.PlannedCheck(zoneSave(target, weapon, request, context.stats(target)), target, target, target.getName() + ": спасбросок"));
            }
            if (targets.isEmpty()) {
                saves.add(new AttackPlan.PlannedCheck(zoneSave(null, weapon, request, Map.of()), null, null, context.targetName() + ": спасбросок"));
            }
            return new AttackPlan(weapon.getName() + " → зона огня", saves, null);
        }
        String names = targets.stream().map(Character::getName).collect(Collectors.joining(", "));
        String title = weapon.getName() + " → " + (targets.isEmpty() ? context.targetName() : names);
        if (Cp2020AreaAttacks.isArea(weapon)) {
            CheckRequest toHit = isPlanted(request) ? null : toHit(context.attacker(), weapon, request, context.stats(context.attacker()));
            AreaDamage blast = Cp2020AreaAttacks.areaDamage(weapon, request, dice);
            return new AttackPlan(title, toHit == null ? List.of() : List.of(new AttackPlan.PlannedCheck(toHit, context.attacker(), null, null)), blast);
        }
        if (targets.size() > 1) {
            throw new IllegalArgumentException("Такой режим огня — только по одной цели");
        }
        CheckRequest toHit = toHit(context.attacker(), weapon, request, context.stats(context.attacker()));
        return new AttackPlan(title, List.of(new AttackPlan.PlannedCheck(toHit, context.attacker(), null, null)), null);
    }

    @Override
    public String spend(AttackContext context) {
        return spend(context.item(), context.request());
    }

    @Override
    public AttackOutcome outcome(AttackContext context, AttackPlan plan, AttackPlan.PlannedCheck planned, CheckResult result, DiceRoller dice) {
        Item weapon = context.item();
        AttackRequest request = context.request();
        if (isZoneFire(request)) {
            boolean safe = Boolean.TRUE.equals(result.success());
            List<AttackOutcome.TargetedHit> hits = safe ? List.of()
                    : zoneHits(weapon, request, dice).stream().map(hit -> new AttackOutcome.TargetedHit(planned.target(), hit)).toList();
            return AttackOutcome.hits(List.of(), hits, null, !safe);
        }
        List<String> steps = new ArrayList<>();
        if (result != null && result.fumble()) {
            steps.addAll(onFumble(weapon, dice));
        }
        boolean landed = result == null || Boolean.TRUE.equals(result.success());
        if (Cp2020AreaAttacks.isArea(weapon)) {
            return landed ? blastLanded(context, plan.blast(), steps, dice) : blastScattered(context, plan.blast(), result, steps, dice);
        }
        if (!landed) {
            return AttackOutcome.miss(steps, result.outcome());
        }
        Character target = context.targets().isEmpty() ? null : context.targets().get(0);
        List<AttackOutcome.TargetedHit> hits = hits(weapon, request, result, dice).stream()
                .map(hit -> new AttackOutcome.TargetedHit(target, hit)).toList();
        return AttackOutcome.hits(steps, hits, hits.isEmpty() ? result.outcome() + ", но ни одна пуля не дошла" : null, !hits.isEmpty());
    }

    private AttackOutcome blastLanded(AttackContext context, AreaDamage blast, List<String> steps, DiceRoller dice) {
        steps.addAll(blast.steps());
        List<AttackOutcome.TargetedHit> hits = new ArrayList<>();
        context.targets().forEach(target -> hits.add(new AttackOutcome.TargetedHit(target, Cp2020AreaAttacks.hit(blast, dice))));
        // цель без листа названа по имени; если никого не выбрали, урон только брошен
        boolean named = context.request().targetName() != null && !context.request().targetName().isBlank();
        if (hits.isEmpty() && named) {
            hits.add(new AttackOutcome.TargetedHit(null, Cp2020AreaAttacks.hit(blast, dice)));
        }
        return AttackOutcome.hits(steps, hits, "Зона " + blast.radius() + " м, задето целей: " + hits.size(), true);
    }

    private AttackOutcome blastScattered(AttackContext context, AreaDamage blast, CheckResult result, List<String> steps, DiceRoller dice) {
        Cp2020AreaAttacks.Scatter scatter = Cp2020AreaAttacks.scatter(blast, dice);
        steps.addAll(scatter.steps());
        steps.addAll(blast.steps());
        return AttackOutcome.scattered(steps, blast, scatter.meters() <= blast.radius() ? context.targets() : List.of(),
                result.outcome() + ": заряд упал мимо");
    }

    @Override
    public AttackHit blastHit(AreaDamage blast, DiceRoller dice) {
        return Cp2020AreaAttacks.hit(blast, dice);
    }

    @Override
    public AttackHit effectHit(String expression, Map<String, String> data, DiceRoller dice) {
        return Cp2020AreaAttacks.effectHit(expression, data, dice);
    }

    /** Заряд подрывается на месте, без броска на попадание */
    boolean isPlanted(AttackRequest request) {
        return PLANT.equals(mode(request));
    }

    /** Форма без статов атакующего: для оружия, дальность которого от них не зависит */
    AttackForm form(Item weapon) {
        return form(weapon, Map.of());
    }

    AttackForm form(Item weapon, Map<String, StatValue> stats) {
        if (!Cp2020ItemTypes.WEAPON.equals(weapon.getType())) {
            return null;
        }
        boolean firearm = !"MELEE".equals(category(weapon)) && number(weapon, Cp2020ItemTypes.SHOTS) >= 1;
        if (!firearm && !isConsumable(weapon)) {
            return null;
        }
        Integer range = isThrown(weapon) ? throwRange(weapon, 1, stats) : range(weapon);
        String unavailable = unavailable(weapon, range);
        List<Option> locations = Arrays.stream(Cp2020Location.values())
                .map(location -> new Option(location.name(), location.getLabel()))
                .toList();
        return new AttackForm(weapon.getProperties().get("damage"), range, unavailable, modes(weapon), maxRounds(weapon),
                defaultRounds(weapon), roundsLabel(weapon),
                skillCodes(weapon).stream().map(code -> new Option(code, skillName(code))).toList(),
                "Зона (выбор −4)", locations, "Расстояние, м", bands(range),
                Cp2020AttackModifiers.sectionsFor(weapon), actionLabel(weapon));
    }

    CheckRequest toHit(Character attacker, Item weapon, AttackRequest request, Map<String, StatValue> stats) {
        AttackForm form = form(weapon, stats);
        if (form.unavailable() != null) {
            throw new IllegalArgumentException(form.unavailable());
        }
        requireMode(weapon, request);
        Cp2020AttackModifiers.requireApplicable(request.options(), weapon);
        int hitNumber = hitNumber(distance(request), rangeFor(weapon, request, form, stats));
        String skillCode = request.skillCode() == null || request.skillCode().isBlank()
                ? skillCodes(weapon).get(0)
                : request.skillCode();
        if (!skillCodes(weapon).contains(skillCode)) {
            throw new IllegalArgumentException("Этот навык не подходит оружию: " + skillCode);
        }
        if (request.hasChosenLocation() && (!SINGLE.equals(mode(request)) || Cp2020AreaAttacks.isArea(weapon))) {
            // HOMEBREW: книга пишет о случайной зоне у каждого попадания и не говорит о прицельной очереди
            throw new IllegalArgumentException("Зону можно выбрать только у одиночного выстрела");
        }
        if (AUTO.equals(mode(request)) && Cp2020AttackModifiers.hasAiming(request.options())) {
            throw new IllegalArgumentException("При автоогне нельзя прицел и прицеливание");
        }

        List<CheckRequest.Part> parts = new ArrayList<>();
        StatValue ref = stats.get(Cp2020StatsStrategy.REF);
        parts.add(new CheckRequest.Part("REF", ref == null ? 0 : ref.value()));
        parts.add(new CheckRequest.Part(skillName(skillCode), skillLevel(attacker, skillCode)));
        parts.add(new CheckRequest.Part("WA", number(weapon, "accuracy")));
        parts.addAll(modeParts(request, hitNumber));
        parts.addAll(Cp2020AttackModifiers.parts(request.options(), hitNumber));
        if (request.hasChosenLocation()) {
            Cp2020Location location = Cp2020Location.parse(request.location());
            parts.add(new CheckRequest.Part("Прицельный выстрел: " + location.getLabel(), Cp2020AttackModifiers.AIMED_SHOT));
        }
        if (request.modifier() != 0) {
            parts.add(new CheckRequest.Part("Мод.", request.modifier()));
        }
        // у автоматического оружия вместо таблицы фамблов надёжность (с. 106), см. onFumble
        return CheckRequest.ofParts("Атака: " + weapon.getName(), parts, hitNumber, isAutomatic(weapon) ? null : skillCode);
    }

    String spend(Item weapon, AttackRequest request) {
        if (isConsumable(weapon)) {
            return Cp2020WeaponActions.consume(weapon, units(weapon, request));
        }
        return Cp2020WeaponActions.fireRounds(weapon, roundsFired(request));
    }

    List<AttackHit> hits(Item weapon, AttackRequest request, CheckResult check, DiceRoller dice) {
        List<String> intro = new ArrayList<>();
        int count = switch (mode(request)) {
            case BURST -> {
                int roll = dice.roll(6);
                int hits = Math.max(1, roll / 2);
                intro.add("Очередь: 1D6/2, d6 = " + roll + " → попало " + hits);
                yield hits;
            }
            case AUTO -> {
                int margin = check.total() - check.difficulty();
                int hits = Math.min(margin, request.rounds());
                intro.add("Автоогонь: итог выше сложности на " + margin + " → попало " + hits);
                yield hits;
            }
            default -> 1;
        };
        return manyHits(weapon, request, intro, count, dice);
    }

    List<String> onFumble(Item weapon, DiceRoller dice) {
        if (!isAutomatic(weapon)) {
            return List.of();
        }
        String reliability = weapon.getProperties().getOrDefault("reliability", "ST");
        int limit = Cp2020Rules.jamLimit(reliability);
        int roll = dice.d10();
        boolean jammed = roll <= limit;
        if (jammed) {
            weapon.getProperties().put(Cp2020ItemTypes.JAMMED, "1");
        }
        return List.of("Надёжность " + reliability + ": d10 = " + roll + ", заклинивает на " + limit + " и ниже"
                + (jammed ? " — заклинило, расклинивать 1D6 раундов" : " — не заклинило"));
    }

    boolean isZoneFire(AttackRequest request) {
        return SUPPRESS.equals(mode(request));
    }

    CheckRequest zoneSave(Character target, Item weapon, AttackRequest request, Map<String, StatValue> stats) {
        AttackForm form = form(weapon);
        if (form.unavailable() != null) {
            throw new IllegalArgumentException(form.unavailable());
        }
        requireMode(weapon, request);
        hitNumber(distance(request), form.range());
        if (request.zoneWidth() < Cp2020Rules.MIN_FIRE_ZONE_WIDTH) {
            throw new IllegalArgumentException("Ширина зоны огня — от " + Cp2020Rules.MIN_FIRE_ZONE_WIDTH + " м");
        }
        int saveNumber = request.rounds() / request.zoneWidth();
        List<CheckRequest.Part> parts = new ArrayList<>();
        if (target == null) {
            parts.add(new CheckRequest.Part("Атлетика + REF цели", request.modifier()));
        } else {
            StatValue ref = stats.get(Cp2020StatsStrategy.REF);
            parts.add(new CheckRequest.Part("REF", ref == null ? 0 : ref.value()));
            parts.add(new CheckRequest.Part(skillName(ATHLETICS), skillLevel(target, ATHLETICS)));
        }
        return CheckRequest.ofParts("Спасбросок от огня: " + weapon.getName(), parts, saveNumber, ATHLETICS);
    }

    List<AttackHit> zoneHits(Item weapon, AttackRequest request, DiceRoller dice) {
        int roll = dice.roll(6);
        return manyHits(weapon, request, List.of("Под огонь: d6 = " + roll + " пуль"), roll, dice);
    }

    /** Каждое попадание со своей зоной и своим уроном; первому достаются шаги о числе попаданий */
    private List<AttackHit> manyHits(Item weapon, AttackRequest request, List<String> intro, int count, DiceRoller dice) {
        List<AttackHit> hits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<String> steps = new ArrayList<>();
            if (i == 0) {
                steps.addAll(intro);
            }
            if (count > 1) {
                steps.add("Попадание " + (i + 1) + " из " + count);
            }
            hits.add(oneHit(weapon, request, steps, dice));
        }
        return hits;
    }

    private AttackHit oneHit(Item weapon, AttackRequest request, List<String> steps, DiceRoller dice) {
        Cp2020Location location;
        if (request.hasChosenLocation()) {
            location = Cp2020Location.parse(request.location());
        } else {
            int roll = dice.d10();
            location = Cp2020Location.fromRoll(roll);
            steps.add("Бросок зоны: d10 = " + roll);
        }

        RolledDamage rolled = rollDamage(weapon, request, steps, dice);
        if (rolled == null) {
            return new AttackHit(location.getLabel(), null, steps, null);
        }
        DamageRequest damageRequest = DamageRequest.builder()
                .amount(rolled.amount())
                .location(location.name())
                .damageType(damageType(weapon))
                .extras(Map.of(Cp2020HealthStrategy.ARMOR_WEAR, armorWear(weapon)))
                .build();
        return new AttackHit(location.getLabel(), rolled.notation(), steps, damageRequest);
    }

    record RolledDamage(String notation, int amount) {
    }

    /**
     * @return null — урон не бросить, в шагах объяснение
     */
    static RolledDamage rollDamage(Item weapon, AttackRequest request, List<String> steps, DiceRoller dice) {
        String damage = weapon.getProperties().get("damage");
        Matcher parsed = damage == null ? null : DAMAGE.matcher(damage.strip());
        if (parsed == null || !parsed.matches()) {
            steps.add("Урон оружия «" + (damage == null ? "не указан" : damage) + "» не бросить: определяет мастер");
            return null;
        }
        DiceExpression expression = forRange(weapon, DiceExpression.parse(parsed.group(1)), request.distance());
        int amount = expression.modifier();
        List<String> rolled = new ArrayList<>();
        for (int i = 0; i < expression.count(); i++) {
            int die = dice.roll(expression.sides());
            rolled.add(String.valueOf(die));
            amount += die;
        }
        String notation = expression + (parsed.group(2) != null ? "/" + parsed.group(2) : "");
        if (parsed.group(2) != null) {
            // Книга (с. 25): деление вниз, значение меньше 1 считается за 1
            amount = Math.max(1, amount / Integer.parseInt(parsed.group(2)));
        }
        steps.add("Урон " + notation + ": " + String.join(" + ", rolled)
                + (expression.modifier() != 0 ? " " + (expression.modifier() > 0 ? "+" : "−") + " " + Math.abs(expression.modifier()) : "")
                + " = " + amount);
        return new RolledDamage(notation, amount);
    }

    static String damageType(Item weapon) {
        return weapon.getProperties().containsKey(Cp2020ItemTypes.ARMOR_PIERCING)
                ? Cp2020DamageType.ARMOR_PIERCING.name()
                : Cp2020DamageType.NORMAL.name();
    }

    static int armorWear(Item weapon) {
        return Math.max(1, number(weapon, Cp2020ItemTypes.ARMOR_WEAR));
    }

    private static List<AttackForm.Mode> modes(Item weapon) {
        if (isConsumable(weapon)) {
            return consumableModes(weapon);
        }
        if ("SHT".equals(category(weapon))) {
            AttackForm.Mode single = new AttackForm.Mode(SINGLE, "Одно пятно", false, false, true, false, false);
            // автодробовик: атак до ROF, с. 109
            return number(weapon, Cp2020ItemTypes.RATE_OF_FIRE) >= BURST_ROUNDS
                    ? List.of(single, new AttackForm.Mode(PATTERNS, "Несколько пятен", true, false, true, false, false))
                    : List.of(single);
        }
        if (weapon.getProperties().containsKey(Cp2020ItemTypes.AREA)) {
            // ракетница, РПГ, огнемёт (с. 99, 110, 111): как граната, но выстрелом; задет всякий в зоне
            return List.of(new AttackForm.Mode(SINGLE, "Выстрел", false, false, true, false, false));
        }
        AttackForm.Mode single = new AttackForm.Mode(SINGLE, "Одиночный", false, false, false, false, true);
        if (!isAutomatic(weapon)) {
            return List.of(single);
        }
        return List.of(single,
                new AttackForm.Mode(BURST, "Очередь из 3", false, false, false, false, false),
                new AttackForm.Mode(AUTO, "Автоогонь", true, false, false, false, false),
                new AttackForm.Mode(SUPPRESS, "Подавляющий огонь", true, true, true, false, false));
    }

    private static List<AttackForm.Mode> consumableModes(Item weapon) {
        boolean area = weapon.getProperties().containsKey(Cp2020ItemTypes.AREA);
        boolean explosive = isExplosive(weapon);
        List<AttackForm.Mode> modes = new ArrayList<>();
        if (isThrown(weapon)) {
            modes.add(area ? new AttackForm.Mode(THROW, "Метнуть", explosive, false, true, false, false)
                    : new AttackForm.Mode(SINGLE, "Метнуть", false, false, false, false, true));
        }
        if (area && weapon.getProperties().containsKey(Cp2020ItemTypes.PLANTED)) {
            modes.add(new AttackForm.Mode(PLANT, "Подорвать на месте", explosive, false, true, true, false));
        }
        return modes;
    }

    /** Кнопка говорит, что случится: брошенный нож уйдёт из рук */
    private static String actionLabel(Item weapon) {
        if (isThrown(weapon)) {
            return "Метнуть";
        }
        return weapon.getProperties().containsKey(Cp2020ItemTypes.PLANTED) ? "Подорвать" : "Атаковать";
    }

    /** Расстояния таблицы одним нажатием: сложность задаёт полоса, а не точное число метров (с. 99) */
    private static List<AttackForm.Band> bands(Integer range) {
        if (range == null || range < 1) {
            return List.of();
        }
        List<AttackForm.Band> bands = new ArrayList<>();
        bands.add(new AttackForm.Band(POINT_BLANK_METERS, "В упор · " + POINT_BLANK));
        int[][] rows = {{range / 4, CLOSE}, {range / 2, MEDIUM}, {range, LONG}, {range * 2, EXTREME}};
        for (int[] row : rows) {
            if (row[0] > POINT_BLANK_METERS) {
                bands.add(new AttackForm.Band(row[0], "до " + row[0] + " м · " + row[1]));
            }
        }
        return bands;
    }

    private static int defaultRounds(Item weapon) {
        if (isExplosive(weapon)) {
            return 1;
        }
        // каждое пятно сверх первого даёт −2: предлагать все 10 значит −18
        return "SHT".equals(category(weapon)) ? Math.min(2, maxRounds(weapon)) : maxRounds(weapon);
    }

    private static int distance(AttackRequest request) {
        if (request.distance() == null) {
            throw new IllegalArgumentException("Укажите расстояние до цели");
        }
        return request.distance();
    }

    private static String unavailable(Item weapon, Integer range) {
        if (isConsumable(weapon)) {
            if (modes(weapon).isEmpty()) {
                return "Задайте зону поражения в правке предмета";
            }
            return isThrown(weapon) && range == null && !weapon.getProperties().containsKey(Cp2020ItemTypes.PLANTED)
                    ? "Дальность зависит от BODY, а счётчика характеристик нет"
                    : null;
        }
        return range == null ? "У оружия не указана дальность: задайте её в правке предмета" : Cp2020WeaponActions.fireBlockedReason(weapon);
    }

    /** Дальность этой атаки: у метательного зависит от BODY и веса заряда */
    private static int rangeFor(Item weapon, AttackRequest request, AttackForm form, Map<String, StatValue> stats) {
        if (!isThrown(weapon)) {
            return form.range();
        }
        Integer range = throwRange(weapon, units(weapon, request), stats);
        if (range == null) {
            throw new IllegalArgumentException("Дальность зависит от BODY, а счётчика характеристик нет");
        }
        if (range <= 0) {
            throw new IllegalArgumentException("Столько не докинуть: " + units(weapon, request) + " кг");
        }
        return range;
    }

    /**
     * Гранаты и взрывчатка: 10 м × BODY, −10 м за кг сверх первого; прочее метательное: BODY × 3 м (с. 99, 108, 109).
     *
     * @return null — BODY неизвестен
     */
    private static Integer throwRange(Item weapon, int units, Map<String, StatValue> stats) {
        StatValue body = stats.get(Cp2020StatsStrategy.BODY);
        if (body == null) {
            return null;
        }
        return weapon.getProperties().containsKey(Cp2020ItemTypes.AREA)
                ? body.value() * GRENADE_METERS_PER_BODY - (units - 1) * METERS_LOST_PER_EXTRA_KG
                : body.value() * THROWN_METERS_PER_BODY;
    }

    private static boolean isThrown(Item weapon) {
        return weapon.getProperties().containsKey(Cp2020ItemTypes.THROWN);
    }

    static boolean isExplosive(Item weapon) {
        return weapon.getProperties().containsKey(Cp2020ItemTypes.EXPLOSIVE);
    }

    /** Расходуется количеством предмета, а не патронами */
    private static boolean isConsumable(Item weapon) {
        return isThrown(weapon) || weapon.getProperties().containsKey(Cp2020ItemTypes.PLANTED);
    }

    /** Сколько единиц уходит за раз: взрывчатка по выбору, остальное по одной */
    static int units(Item weapon, AttackRequest request) {
        return isExplosive(weapon) ? request.rounds() : 1;
    }

    private static int quantity(Item item) {
        return item.getQuantity() == null ? 0 : item.getQuantity();
    }

    static void requireMode(Item weapon, AttackRequest request) {
        String mode = mode(request);
        if (modes(weapon).stream().noneMatch(known -> known.code().equals(mode))) {
            throw new IllegalArgumentException("У этого оружия нет такого режима огня");
        }
        if (isConsumable(weapon)) {
            int units = units(weapon, request);
            if (units < 1 || units > quantity(weapon)) {
                throw new IllegalArgumentException("Можно израсходовать от 1 до " + quantity(weapon));
            }
            return;
        }
        if (AUTO.equals(mode) || SUPPRESS.equals(mode) || PATTERNS.equals(mode)) {
            int rof = number(weapon, Cp2020ItemTypes.RATE_OF_FIRE);
            if (request.rounds() < 1 || request.rounds() > rof) {
                throw new IllegalArgumentException("Патронов за раунд — от 1 до " + rof + " (ROF)");
            }
        }
        int loaded = number(weapon, Cp2020ItemTypes.LOADED);
        if (roundsFired(request) > loaded) {
            throw new IllegalArgumentException("В магазине только " + loaded);
        }
    }

    private static List<CheckRequest.Part> modeParts(AttackRequest request, int hitNumber) {
        return switch (mode(request)) {
            case BURST -> hitNumber <= MEDIUM ? List.of(new CheckRequest.Part("Очередь из 3", BURST_BONUS)) : List.of();
            case AUTO -> {
                int tens = request.rounds() / ROUNDS_PER_STEP;
                yield tens == 0 ? List.of()
                        : List.of(new CheckRequest.Part("Автоогонь, " + request.rounds() + " патр.", hitNumber <= CLOSE ? tens : -tens));
            }
            // по примеру книги (с. 109): 5 выстрелов из CAWS — один бросок с −8
            case PATTERNS -> request.rounds() < 2 ? List.of()
                    : List.of(new CheckRequest.Part("Пятен: " + request.rounds(), (request.rounds() - 1) * EXTRA_PATTERN_PENALTY));
            default -> List.of();
        };
    }

    private static int roundsFired(AttackRequest request) {
        return switch (mode(request)) {
            case BURST -> BURST_ROUNDS;
            case AUTO, SUPPRESS, PATTERNS -> request.rounds();
            default -> 1;
        };
    }

    static String mode(AttackRequest request) {
        return request.mode() == null || request.mode().isBlank() ? SINGLE : request.mode();
    }

    /**
     * HOMEBREW: книга не делит оружие на автоматическое и нет; считаем таким то, что выпускает очередь из 3 за раунд.
     * Автоматические дробовики стреляют пятнами (с. 109), см. {@link #modes}.
     */
    static boolean isAutomatic(Item weapon) {
        return number(weapon, Cp2020ItemTypes.RATE_OF_FIRE) >= BURST_ROUNDS && !"SHT".equals(category(weapon));
    }

    private static int maxRounds(Item weapon) {
        if (isConsumable(weapon)) {
            return quantity(weapon);
        }
        return Math.min(number(weapon, Cp2020ItemTypes.RATE_OF_FIRE), number(weapon, Cp2020ItemTypes.LOADED));
    }

    private static String roundsLabel(Item weapon) {
        if (isExplosive(weapon)) {
            return "Единиц, " + weapon.getProperties().getOrDefault(Cp2020ItemTypes.UNIT, "кг");
        }
        return "SHT".equals(category(weapon)) ? "Пятен" : "Патронов";
    }

    /**
     * Сложность по дальности (с. 99, 105): в упор до 1 м, близко до ¼, средне до ½, далеко до полной, предельно до двойной.
     *
     * @throws IllegalArgumentException если цель дальше двойной дальности
     */
    static int hitNumber(int distance, int range) {
        if (distance < 0) {
            throw new IllegalArgumentException("Расстояние не может быть отрицательным");
        }
        // сравнение умножением: у дальности 50 четверть не целая
        if (distance <= POINT_BLANK_METERS) return POINT_BLANK;
        if (distance * 4 <= range) return CLOSE;
        if (distance * 2 <= range) return MEDIUM;
        if (distance <= range) return LONG;
        if (distance <= range * 2) return EXTREME;
        throw new IllegalArgumentException("Цель вне дальности: " + distance + " м, предел этого оружия " + range * 2 + " м");
    }

    /**
     * Дробовик теряет кубики с расстоянием (с. 109): близко и в упор 4D6, средне 3D6, далеко 2D6; на предельной как далеко (с. 99).
     * HOMEBREW: у дробовика с другим уроном снимается столько же кубиков, не меньше одного.
     */
    private static DiceExpression forRange(Item weapon, DiceExpression damage, int distance) {
        if (!"SHT".equals(category(weapon))) {
            return damage;
        }
        int hitNumber = hitNumber(distance, range(weapon));
        int lost = hitNumber <= CLOSE ? 0 : hitNumber == MEDIUM ? 1 : 2;
        return new DiceExpression(Math.max(1, damage.count() - lost), damage.sides(), damage.modifier());
    }

    /** Дальность предмета, иначе — общая для его типа (с. 99): у старого оружия своей в книге нет */
    static Integer range(Item weapon) {
        if (weapon.getProperties().containsKey("range")) {
            return number(weapon, "range");
        }
        return switch (String.valueOf(category(weapon))) {
            case "P", "SHT" -> 50;
            case "SMG" -> 150;
            case "RIF" -> 400;
            default -> null;
        };
    }

    /** Книга: дробовик — навык Rifle. Экзотическое оружие по навыкам не делится, поэтому выбор */
    private static List<String> skillCodes(Item weapon) {
        if (isThrown(weapon)) {
            // книга называет навык Throwing, в списке навыков его нет: это Athletics
            return List.of(ATHLETICS);
        }
        return switch (String.valueOf(category(weapon))) {
            case "P" -> List.of("handgun");
            case "SMG" -> List.of("submachinegun");
            case "SHT", "RIF" -> List.of("rifle");
            case "HVY" -> List.of("heavy_weapons");
            default -> EXOTIC_SKILLS;
        };
    }

    private static String skillName(String code) {
        return Cp2020Skills.CATALOG.stream()
                .filter(definition -> definition.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет такого навыка: " + code))
                .name();
    }

    private static int skillLevel(Character character, String code) {
        return character.getSkills().stream()
                .filter(skill -> code.equals(skill.getSkillCode()))
                .mapToInt(CharacterSkill::getLevel)
                .findFirst()
                .orElse(0);
    }

    static String category(Item weapon) {
        return weapon.getProperties().get("category");
    }

    static int number(Item item, String key) {
        String value = item.getProperties().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
