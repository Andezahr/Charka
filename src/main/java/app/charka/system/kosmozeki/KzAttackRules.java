package app.charka.system.kosmozeki;

import app.charka.attack.AttackContext;
import app.charka.attack.AttackForm;
import app.charka.attack.AttackHit;
import app.charka.attack.AttackOutcome;
import app.charka.attack.AttackPlan;
import app.charka.attack.AttackRules;
import app.charka.attack.AttackSource;
import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.item.ItemField.Option;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.StatValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Атака Космозэков (K-05, K-06, раздел 5 {@code RULES_KOSMOZEKI.md}).
 * <ul>
 *   <li>Стрельба: сложность = DIF + модификатор − выпущено пуль; к100 не меньше — попал; попаданий 1 + ⌊разница / 10⌋, не больше пуль;
 *   зона — последняя цифра броска, прицельно +15 (голова +30); 100 — все в голову и ещё урон пули сверху.</li>
 *   <li>Ближний бой: бросок на каждую атаку против DIF оружия; броня против «броня ½» вдвое меньше.</li>
 *   <li>Взрывчатка: бросок попадания; не убежавшим — урон в зоны, которые выбрал мастер (не выбрал — во все).</li>
 *   <li>Драка: 1к12 против 1к12 защищающегося, больше — попал; урон вводит атакующий.</li>
 * </ul>
 * Модификаторы сильно зависят от мастера: подсказки из таблицы правил и поле для любого числа.
 */
public class KzAttackRules implements AttackRules {

    public static final String FIRE = "FIRE";
    public static final String STRIKE = "STRIKE";
    public static final String THROW = "THROW";
    public static final String BRAWL = "BRAWL";

    static final int AIM_LIMB = 15;
    static final int AIM_HEAD = 30;
    /** Прибавка в ход к10 драки */
    static final int BRAWL_DIE = 12;
    /** HOMEBREW: урон кулака по умолчанию — в правилах его нет, атакующий вводит свой */
    static final int BRAWL_DAMAGE = 10;
    /** Флажок «Сложность вдвое» (способность K-10) */
    public static final String HALVE = "halve";
    /** Префикс флажков зон взрыва */
    static final String ZONE_OPTION = "zone_";

    /** Подсказки модификаторов из таблицы правил: код → (подпись, число) */
    private static final Map<String, Integer> MODIFIERS = new LinkedHashMap<>();
    private static final Map<String, String> MODIFIER_LABELS = new LinkedHashMap<>();
    private static final List<AttackForm.Section> SECTIONS;

    static {
        List<AttackForm.Choice> common = List.of(
                choice("light", "Освещение", opt("light5", "Сумерки +5", 5), opt("light10", "Дым, туман +10", 10),
                        opt("dark", "Темнота, вслепую +40", 40)),
                choice("shooter", "Стрелок двигается", opt("move10", "+10", 10), opt("move20", "Бежит +20", 20)),
                choice("target", "Цель двигается", opt("tmove10", "+10", 10), opt("tmove20", "Быстро +20", 20)),
                choice("prep", "Подготовка", opt("prep5", "Удобная позиция −5", -5), opt("prep10", "Прицел, сошки −10", -10),
                        opt("prep20", "Прицел и упор −20", -20), opt("prep30", "Всё готово −30", -30)));
        List<AttackForm.Choice> rare = List.of(
                choice("far", "Цель очень далеко", opt("far", "Очень далеко +20", 20)),
                choice("close", "В упор (меньше 1 м)", opt("close-10", "Удобно −10", -10), opt("close10", "Неудобно +10", 10)),
                choice("dual", "С двух рук", opt("dual15", "+15", 15), opt("dual20", "+20", 20)));
        SECTIONS = List.of(new AttackForm.Section("Условия", common, false), new AttackForm.Section("Ещё", rare, true));
    }

    private record Opt(String code, String label, int value) {
    }

    private static Opt opt(String code, String label, int value) {
        MODIFIERS.put(code, value);
        MODIFIER_LABELS.put(code, label);
        return new Opt(code, label, value);
    }

    private static AttackForm.Choice choice(String key, String label, Opt... options) {
        List<Option> list = new ArrayList<>();
        for (Opt option : options) {
            list.add(new Option(option.code(), option.label()));
        }
        return new AttackForm.Choice(key, label, list);
    }

    /** HOMEBREW: прицел в тело — как в конечность (+15); в правилах только «конечность» и «голова» */
    private static final List<Option> AIM = List.of(
            new Option(KzBody.HEAD, "Голова +" + AIM_HEAD),
            new Option(KzBody.BODY, "Тело +" + AIM_LIMB),
            new Option(KzBody.RIGHT_ARM, "Правая рука +" + AIM_LIMB),
            new Option(KzBody.LEFT_ARM, "Левая рука +" + AIM_LIMB),
            new Option(KzBody.RIGHT_LEG, "Правая нога +" + AIM_LIMB),
            new Option(KzBody.LEFT_LEG, "Левая нога +" + AIM_LIMB));

    // ---------- форма ----------

    @Override
    public AttackForm form(AttackSource source, Map<String, StatValue> stats) {
        if (BRAWL.equals(source.ability())) {
            AttackForm.Mode mode = new AttackForm.Mode(BRAWL, "Удар", true, false, false, false, true, true);
            return new AttackForm("1к12 против 1к12", null, null, List.of(mode), 500, BRAWL_DAMAGE, "урон удара", List.of(),
                    "Куда бить", AIM, null, List.of(), List.of(), "Ударить");
        }
        Item item = source.item();
        if (item == null) {
            return null;
        }
        List<AttackForm.Section> sections = withHalve(item);
        int dmg = KzItems.number(item, KzItems.DMG, 0);
        if (KzItems.isRanged(item)) {
            int loaded = KzItems.loaded(item);
            int rof = Math.max(1, KzItems.number(item, KzItems.ROF, 1));
            int max = Math.max(1, Math.min(rof, loaded));
            AttackForm.Mode mode = new AttackForm.Mode(FIRE, "Огонь", true, false, false, false, true, true, true);
            return new AttackForm(dmg + " за пулю", null, loaded == 0 ? "Магазин пуст — перезарядите" : null, List.of(mode),
                    max, max, "пуль, DIF −1 за каждую", List.of(), "Прицельно", AIM, null, List.of(), sections, "Стрелять");
        }
        if (KzItems.isMelee(item)) {
            int attacks = Math.max(1, KzItems.number(item, KzItems.ATTACKS, 1));
            AttackForm.Mode mode = new AttackForm.Mode(STRIKE, "Удар", true, false, false, false, true, true);
            return new AttackForm(dmg + (item.getProperties().containsKey(KzItems.ARMOR_HALF) ? " · броня ½" : ""), null, null,
                    List.of(mode), attacks, attacks, "атак", List.of(), "Прицельно", AIM, null, List.of(), sections, "Ударить");
        }
        if (KzItems.isExplosive(item)) {
            AttackForm.Mode mode = new AttackForm.Mode(THROW, "Бросок", false, false, true, false, false, true, false);
            List<AttackForm.Section> withZones = new ArrayList<>(sections);
            List<AttackForm.Choice> zones = new ArrayList<>();
            for (Option zone : AIM) {
                zones.add(new AttackForm.Choice(ZONE_OPTION + zone.value(),
                        null, List.of(new Option(ZONE_OPTION + zone.value(), zone.label().replaceAll(" \\+\\d+$", "")))));
            }
            withZones.add(0, new AttackForm.Section("Зоны (не выбрано — все)", zones, false));
            return new AttackForm(dmg + " в каждую зону", null, null, List.of(mode), 1, 1, "", List.of(), null, List.of(),
                    null, List.of(), withZones, "Бросить");
        }
        return null;
    }

    /** Подсказки модификаторов; флажок «Сложность вдвое» — если способность ещё есть в этом бою */
    private static List<AttackForm.Section> withHalve(Item item) {
        Character owner = item.getInventory() == null ? null : item.getInventory().getCharacter();
        if (owner == null || KzAbilities.halveCounter(owner).filter(counter -> KzAbilities.charges(counter) > 0).isEmpty()) {
            return SECTIONS;
        }
        List<AttackForm.Section> sections = new ArrayList<>();
        sections.add(new AttackForm.Section("Способность", List.of(new AttackForm.Choice(HALVE, null,
                List.of(new Option(HALVE, "Сложность вдвое (раз за бой)")))), false));
        sections.addAll(SECTIONS);
        return sections;
    }

    @Override
    public List<AttackSource> abilities(Character attacker) {
        return List.of(AttackSource.ability(BRAWL, "Драка"));
    }

    @Override
    public String attackBase(Character attacker, AttackSource source, AttackForm form, Map<String, StatValue> stats) {
        Item item = source.item();
        if (item == null || !KzItems.isWeapon(item)) {
            return null;
        }
        return "DIF " + KzItems.number(item, KzItems.DIF, 0) + (KzItems.isRanged(item) ? " − пули" : "");
    }

    // ---------- план ----------

    @Override
    public AttackPlan plan(AttackContext context, DiceRoller dice) {
        Item item = context.item();
        String title = context.source().name() + " → " + targetLabel(context);
        if (item == null) {
            // драка: бросают 1к12 оба — в итоге, без общей проверки системы
            return new AttackPlan(title, List.of(), null);
        }
        Character target = context.targets().isEmpty() ? null : context.targets().get(0);
        int base = KzItems.number(item, KzItems.DIF, 0);
        Modifiers modifiers = modifiers(context);
        if (KzItems.isRanged(item)) {
            int rounds = rounds(context, item);
            Difficulty difficulty = difficulty(base, modifiers, rounds, context.request().options().contains(HALVE));
            return new AttackPlan(title, List.of(new AttackPlan.PlannedCheck(new CheckRequest("Попадание", 0, difficulty.value()),
                    context.attacker(), target, difficulty.step())), null);
        }
        if (KzItems.isMelee(item)) {
            int attacks = Math.max(1, Math.min(KzItems.number(item, KzItems.ATTACKS, 1), context.request().rounds() > 0 ? context.request().rounds() : 99));
            Difficulty difficulty = difficulty(base, modifiers, 0, context.request().options().contains(HALVE));
            List<AttackPlan.PlannedCheck> checks = new ArrayList<>();
            for (int i = 1; i <= attacks; i++) {
                checks.add(new AttackPlan.PlannedCheck(new CheckRequest("Удар", 0, difficulty.value()), context.attacker(), target,
                        attacks > 1 ? "Удар " + i + " из " + attacks + (i == 1 ? ": " + difficulty.step() : "") : difficulty.step()));
            }
            return new AttackPlan(title, checks, null);
        }
        // взрывчатка: один бросок, урон всем выбранным целям
        Difficulty difficulty = difficulty(base, modifiers, 0, context.request().options().contains(HALVE));
        return new AttackPlan(title, List.of(new AttackPlan.PlannedCheck(new CheckRequest("Бросок", 0, difficulty.value()),
                context.attacker(), null, difficulty.step())), null);
    }

    private static String targetLabel(AttackContext context) {
        if (context.targets().isEmpty()) {
            return context.targetName();
        }
        return String.join(", ", context.targets().stream().map(Character::getName).toList());
    }

    /** Сколько пуль: 1…ROF, не больше, чем в магазине */
    static int rounds(AttackContext context, Item item) {
        int rof = Math.max(1, KzItems.number(item, KzItems.ROF, 1));
        int loaded = KzItems.loaded(item);
        int wanted = context.request().rounds() > 0 ? context.request().rounds() : Math.min(rof, loaded);
        if (loaded <= 0) {
            throw new IllegalArgumentException("Магазин пуст — перезарядите");
        }
        return Math.max(1, Math.min(wanted, Math.min(rof, loaded)));
    }

    /**
     * Модификатор сложности: подсказки, прицел и число мастера.
     *
     * @param total  сумма
     * @param labels из чего сложилась: «Дым +10», «Прицел: голова +30»
     */
    record Modifiers(int total, List<String> labels) {
    }

    static Modifiers modifiers(AttackContext context) {
        int total = 0;
        List<String> labels = new ArrayList<>();
        for (String code : context.request().options()) {
            Integer value = MODIFIERS.get(code);
            if (value != null) {
                total += value;
                labels.add(MODIFIER_LABELS.get(code));
            }
        }
        if (context.request().hasChosenLocation() && !(context.item() != null && KzItems.isExplosive(context.item()))) {
            int aim = KzBody.HEAD.equals(context.request().location()) ? AIM_HEAD : AIM_LIMB;
            total += aim;
            labels.add("прицел " + signed(aim));
        }
        if (context.request().modifier() != 0) {
            total += context.request().modifier();
            labels.add("мастер " + signed(context.request().modifier()));
        }
        return new Modifiers(total, labels);
    }

    /**
     * @param value итоговая сложность
     * @param step  как посчитана
     */
    record Difficulty(int value, String step) {
    }

    /**
     * Сложность = DIF + модификаторы − пули. «Сложность вдвое» — как выгоднее стрелку: из «база пополам, потом остальное»
     * и «итог пополам» берётся меньшее (ответ автора 2026-11-07).
     */
    static Difficulty difficulty(int base, Modifiers modifiers, int rounds, boolean halve) {
        int plain = base + modifiers.total() - rounds;
        StringBuilder step = new StringBuilder("Сложность: DIF " + base);
        if (modifiers.total() != 0) {
            step.append(" ").append(signed(modifiers.total())).append(" (").append(String.join(", ", modifiers.labels())).append(")");
        }
        if (rounds > 0) {
            step.append(" − ").append(rounds).append(rounds == 1 ? " пуля" : " пуль");
        }
        step.append(" = ").append(plain);
        int value = plain;
        if (halve) {
            int halvedBase = Math.floorDiv(base, 2) + modifiers.total() - rounds;
            int halvedTotal = Math.floorDiv(plain, 2);
            value = Math.min(halvedBase, halvedTotal);
            step.append("; вдвое → ").append(value);
        }
        return new Difficulty(value, step.toString());
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    // ---------- расход ----------

    @Override
    public String spend(AttackContext context) {
        Item item = context.item();
        List<String> spent = new ArrayList<>();
        if (context.request().options().contains(HALVE)) {
            KzAbilities.spendHalve(context.attacker());
            spent.add("Способность «Сложность вдвое» потрачена до конца боя");
        }
        // патроны дальнего оружия списывает итог выстрела: ему нужно знать, сколько было в магазине
        if (item != null && KzItems.isExplosive(item)) {
            int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
            if (quantity <= 1) {
                item.getInventory().getContent().remove(item);
                spent.add(item.getName() + ": последняя");
            } else {
                item.setQuantity(quantity - 1);
                spent.add(item.getName() + ": осталось " + (quantity - 1));
            }
        }
        return spent.isEmpty() ? null : String.join("; ", spent);
    }

    // ---------- итог ----------

    @Override
    public AttackOutcome outcome(AttackContext context, AttackPlan plan, AttackPlan.PlannedCheck planned, CheckResult result, DiceRoller dice) {
        Item item = context.item();
        if (item == null) {
            return brawl(context, dice);
        }
        boolean success = Boolean.TRUE.equals(result.success());
        List<String> steps = new ArrayList<>();
        int rounds = 0;
        if (KzItems.isRanged(item)) {
            rounds = rounds(context, item);
            int loaded = KzItems.loaded(item);
            item.getProperties().put(KzItems.LOADED, String.valueOf(loaded - rounds));
            steps.add("В магазине: " + loaded + " → " + (loaded - rounds));
        }
        if (!success) {
            if (result.fumble()) {
                steps.add("Выпала 1: случилось что-то плохое — решает мастер");
            }
            return AttackOutcome.miss(steps, result.fumble() ? "Крит. провал" : "Мимо");
        }
        int dmg = KzItems.number(item, KzItems.DMG, 0);
        int digit = result.total() % 10;
        boolean crit = result.critical();
        if (KzItems.isExplosive(item)) {
            return blast(context, item, dmg, steps);
        }
        int hits = 1;
        if (KzItems.isRanged(item)) {
            int difficulty = result.difficulty() == null ? 0 : result.difficulty();
            int extra = Math.max(0, (result.total() - difficulty) / 10);
            hits = Math.min(rounds, 1 + extra);
            steps.add("Попаданий: 1 + " + extra + " (" + result.total() + " − " + difficulty + ")"
                    + (1 + extra > rounds ? ", но выпущено " + rounds : "") + " = " + hits);
        }
        String location = context.request().hasChosenLocation() ? context.request().location() : null;
        Map<String, Integer> extras = new LinkedHashMap<>();
        extras.put(KzBodyStrategy.HITS, hits);
        if (KzItems.isMelee(item) && item.getProperties().containsKey(KzItems.ARMOR_HALF)) {
            extras.put(KzBodyStrategy.ARMOR_HALF, 1);
        }
        if (crit) {
            location = KzBody.HEAD;
            // HOMEBREW: «дополнительный урон» крита — ещё урон одной пули или удара; мастер может поправить ХП вручную
            extras.put(KzBodyStrategy.BONUS, dmg);
            steps.add("100 — крит: все попадания в голову, ещё +" + dmg + " урона");
        } else if (location == null) {
            extras.put(KzBodyStrategy.DIGIT, digit);
        }
        DamageRequest damage = DamageRequest.builder().amount(dmg).location(location).extras(extras).build();
        String where = location != null ? zoneName(location) : "зона по цифре " + digit;
        AttackHit hit = new AttackHit(where, hits > 1 ? hits + "×" + dmg : String.valueOf(dmg), List.of(), damage);
        Character target = planned.target();
        return AttackOutcome.hits(steps, List.of(new AttackOutcome.TargetedHit(target, hit)), null, true);
    }

    /** Взрыв: каждой цели — урон в выбранные зоны (или во все её зоны) */
    private AttackOutcome blast(AttackContext context, Item item, int dmg, List<String> steps) {
        List<String> chosen = context.request().options().stream()
                .filter(option -> option.startsWith(ZONE_OPTION))
                .map(option -> option.substring(ZONE_OPTION.length()))
                .toList();
        List<AttackOutcome.TargetedHit> hits = new ArrayList<>();
        List<Character> targets = context.targets();
        if (targets.isEmpty()) {
            steps.add("Урон " + dmg + (chosen.isEmpty() ? " во все зоны" : " в зоны: " + String.join(", ", chosen.stream().map(KzAttackRules::zoneName).toList())));
            return AttackOutcome.hits(steps, List.of(), "Попал: " + context.targetName(), true);
        }
        for (Character target : targets) {
            KzBody body = KzAbilities.bodyCounter(target).map(KzBodyStrategy::body).orElse(KzBody.HUMAN);
            List<KzBody.Zone> zones = chosen.isEmpty() ? body.zones()
                    : body.zones().stream().filter(zone -> chosen.contains(zone.code())).toList();
            if (zones.isEmpty()) {
                zones = body.zones();
            }
            for (KzBody.Zone zone : zones) {
                DamageRequest damage = DamageRequest.builder().amount(dmg).location(zone.code()).build();
                hits.add(new AttackOutcome.TargetedHit(target, new AttackHit(zone.name(), String.valueOf(dmg),
                        List.of(target.getName() + ", " + zone.name() + ":"), damage)));
            }
        }
        return AttackOutcome.hits(steps, hits, "Взрыв: " + targets.size() + (targets.size() == 1 ? " цель" : " целей"), true);
    }

    /** Драка (K-06): 1к12 против 1к12 защищающегося; больше — попал, ничья — промах */
    private AttackOutcome brawl(AttackContext context, DiceRoller dice) {
        List<String> steps = new ArrayList<>();
        Character target = context.targets().isEmpty() ? null : context.targets().get(0);
        int attack = KzAbilities.d12(context.attacker(), dice, steps, context.attacker().getName());
        int defence = target != null ? KzAbilities.d12(target, dice, steps, target.getName()) : dice.roll(BRAWL_DIE);
        if (target == null) {
            steps.add(context.targetName() + ": к12 = " + defence);
        }
        // HOMEBREW: при равенстве удар не прошёл — защищающийся удержался
        if (attack <= defence) {
            steps.add(attack + " против " + defence + (attack == defence ? " — ничья, удар не прошёл" : " — не попал"));
            return AttackOutcome.miss(steps, "Мимо");
        }
        steps.add(attack + " против " + defence + " — попал");
        int amount = Math.max(0, context.request().rounds() > 0 ? context.request().rounds() : BRAWL_DAMAGE);
        String location = context.request().hasChosenLocation() ? context.request().location() : null;
        DamageRequest.DamageRequestBuilder damage = DamageRequest.builder().amount(amount).location(location);
        AttackHit hit = new AttackHit(location != null ? zoneName(location) : "случайная зона", String.valueOf(amount), List.of(), damage.build());
        return AttackOutcome.hits(steps, List.of(new AttackOutcome.TargetedHit(target, hit)), null, true);
    }

    static String zoneName(String code) {
        return KzBody.HUMAN.zone(code).map(KzBody.Zone::name).orElse(code);
    }

    /** Цель выбыла: тело на нуле или мёртв — для подсказки в трекере */
    static boolean out(Character character) {
        Optional<Counter> body = KzAbilities.bodyCounter(character);
        return body.isPresent() && KzBodyStrategy.out(body.get());
    }
}
