package app.charka.system.cp2020;

import app.charka.attack.AttackContext;
import app.charka.attack.AttackForm;
import app.charka.attack.AttackHit;
import app.charka.attack.AttackOutcome;
import app.charka.attack.AttackPlan;
import app.charka.attack.AttackRequest;
import app.charka.attack.AttackSource;
import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.item.ItemField.Option;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.StatValue;
import app.charka.system.cp2020.Cp2020MartialArts.Move;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ближний бой (с. 111–112): встречный бросок REF + навык + 1D10, равенство за защитой (с. 42). К урону прибавка от BODY, у боевых искусств ещё их уровень.
 * Захваты, броски, удержания, подсечки — C-10.
 */
final class Cp2020Melee {

    /** Удар оружием */
    static final String MELEE = "MELEE";
    static final String STRIKE = "STRIKE";
    static final String KICK = "KICK";
    /** Голыми руками: способность, а не предмет */
    static final String BRAWL = "BRAWL";
    static final String BRAWL_NAME = "Рукопашная";

    static final String DEFENSE = "defense";
    static final String DODGING = "target_dodges";
    static final String PARRYING = "target_parries";

    private static final int DODGE_PENALTY = -2;
    private static final int AIMED = Cp2020AttackModifiers.AIMED_SHOT;
    /** «1D6/2 + Damage Modifier» и «1D6 + Damage Modifier» (с. 111) */
    private static final String STRIKE_DAMAGE = "1D6/2";
    private static final String KICK_DAMAGE = "1D6";

    /** Книга (с. 111): «Martial arts, Fencing, Melee, Dodge or Athletics can be used» */
    private static final List<String> DEFENSE_SKILLS = Stream.concat(
            Stream.of("melee", "fencing", "dodge_escape", "athletics"), Cp2020MartialArts.STYLES.stream()).toList();
    private static final List<String> WEAPON_SKILLS = List.of("melee", "fencing");
    private static final List<String> BRAWL_SKILLS = Stream.concat(Stream.of("brawling"), Cp2020MartialArts.STYLES.stream()).toList();

    private Cp2020Melee() {
    }

    static AttackSource brawl() {
        return AttackSource.ability(BRAWL, BRAWL_NAME);
    }

    /** Эта атака — ближний бой: у ножа есть и бросок, и удар */
    static boolean applies(AttackContext context) {
        return context.item() == null || isMeleeMode(context.request());
    }

    private static boolean isMeleeMode(AttackRequest request) {
        return MELEE.equals(request.mode()) || STRIKE.equals(request.mode()) || KICK.equals(request.mode());
    }

    /** Холодное оружие; метательное только с дальностью удара в книге: у сюрикена её нет */
    static boolean hasMeleeMode(Item weapon) {
        return "MELEE".equals(Cp2020AttackRules.category(weapon))
                && (!weapon.getProperties().containsKey(Cp2020ItemTypes.THROWN) || weapon.getProperties().containsKey("range"));
    }

    static boolean isDefense(String code) {
        return DODGING.equals(code) || PARRYING.equals(code);
    }

    static AttackForm.Choice defenseChoice() {
        return new AttackForm.Choice(DEFENSE, "Цель защищается", List.of(
                new Option(DODGING, "Уклоняется −2 к атаке"),
                new Option(PARRYING, "Парирует: урон в предмет")));
    }

    static AttackForm.Mode weaponMode() {
        return new AttackForm.Mode(MELEE, "Ударить", false, false, false, false, true, true);
    }

    static AttackForm weaponForm(Item weapon) {
        return form(weapon.getProperties().get("damage"), List.of(weaponMode()), WEAPON_SKILLS);
    }

    static AttackForm brawlForm() {
        return form(STRIKE_DAMAGE + " + BODY", List.of(
                new AttackForm.Mode(STRIKE, "Удар " + STRIKE_DAMAGE, false, false, false, false, true, true),
                new AttackForm.Mode(KICK, "Пинок " + KICK_DAMAGE, false, false, false, false, true, true)), BRAWL_SKILLS);
    }

    private static AttackForm form(String damage, List<AttackForm.Mode> modes, List<String> skills) {
        return new AttackForm(damage, null, null, modes, 1, 1, "", skillOptions(skills),
                "Зона (выбор −4)", locations(), null, List.of(), Cp2020AttackModifiers.meleeSections(), "Атаковать");
    }

    private static List<Option> locations() {
        return Arrays.stream(Cp2020Location.values()).map(location -> new Option(location.name(), location.getLabel())).toList();
    }

    /** Пустое значение — лучший навык: чаще всего выбирать незачем */
    private static List<Option> skillOptions(List<String> skills) {
        List<Option> options = new ArrayList<>();
        options.add(new Option("", "Лучший навык"));
        skills.forEach(code -> options.add(new Option(code, skillName(code))));
        return options;
    }

    static AttackPlan plan(AttackContext context, DiceRoller dice) {
        List<Character> targets = context.targets();
        if (targets.size() > 1) {
            throw new IllegalArgumentException("Ударить можно только одну цель");
        }
        AttackRequest request = context.request();
        Character attacker = context.attacker();
        Character target = targets.isEmpty() ? null : targets.get(0);
        Move move = move(request);
        List<String> skills = context.item() == null ? BRAWL_SKILLS : WEAPON_SKILLS;
        String skill = chosenSkill(attacker, request, skills, move);

        List<CheckRequest.Part> parts = new ArrayList<>();
        parts.add(new CheckRequest.Part("REF", ref(context.stats(attacker))));
        parts.add(new CheckRequest.Part(skillName(skill), level(attacker, skill)));
        if (context.item() != null) {
            parts.add(new CheckRequest.Part("WA", Cp2020AttackRules.number(context.item(), "accuracy")));
        }
        if (keyBonus(attacker, skill, move) != 0) {
            parts.add(new CheckRequest.Part("Ключевой приём", keyBonus(attacker, skill, move)));
        }
        if (request.hasChosenLocation()) {
            parts.add(new CheckRequest.Part("Прицельный удар: " + Cp2020Location.parse(request.location()).getLabel(), AIMED));
        }
        if (request.options().contains(DODGING)) {
            parts.add(new CheckRequest.Part("Цель уклоняется", DODGE_PENALTY));
        }
        Set<String> others = new HashSet<>(request.options());
        others.removeIf(Cp2020Melee::isDefense);
        parts.addAll(Cp2020AttackModifiers.parts(others, 0));
        if (target != null && request.modifier() != 0) {
            parts.add(new CheckRequest.Part("Мод.", request.modifier()));
        }

        Cp2020Dice.Roll defense = Cp2020Dice.rollCheck(dice);
        int defenseBase;
        String intro;
        if (target == null) {
            // цель без листа: мастер вводит её REF + навык в «Прочий модификатор», как в зоне огня
            defenseBase = request.modifier();
            intro = "Защита «" + context.targetName() + "»: REF и навык " + defenseBase + " + d10 " + defense.describe();
        } else {
            Move key = request.options().contains(DODGING) ? Move.DODGE : request.options().contains(PARRYING) ? Move.BLOCK : null;
            String defenseSkill = chosenSkill(target, null, DEFENSE_SKILLS, key);
            int ref = ref(context.stats(target));
            int bonus = keyBonus(target, defenseSkill, key);
            defenseBase = ref + level(target, defenseSkill) + bonus;
            intro = "Защита " + target.getName() + ": REF " + ref + " + " + skillName(defenseSkill) + " " + level(target, defenseSkill)
                    + (bonus != 0 ? " + ключевой приём " + bonus : "") + " + d10 " + defense.describe();
        }
        int total = defenseBase + defense.total();
        intro += " = " + total + "; равенство за защитой (с. 42)";

        String weapon = context.source().name();
        String title = weapon + " → " + (target != null ? target.getName() : context.targetName());
        CheckRequest toHit = CheckRequest.ofParts("Удар: " + weapon, parts, total + 1, skill);
        return new AttackPlan(title, List.of(new AttackPlan.PlannedCheck(toHit, attacker, target, intro)), null);
    }

    static AttackOutcome outcome(AttackContext context, CheckResult result, DiceRoller dice) {
        Item weapon = context.item();
        boolean mono = weapon != null && Cp2020DamageType.MONO.name().equals(weapon.getProperties().get(Cp2020ItemTypes.EDGE));
        List<String> steps = new ArrayList<>();
        if (result.fumble() && mono) {
            // С. 112: моно-оружие на фамбле ломается всегда
            steps.add(Cp2020WeaponActions.consume(weapon, 1) + ": моно-лезвие сломалось на критическом провале");
        }
        if (!Boolean.TRUE.equals(result.success())) {
            return AttackOutcome.miss(steps, result.outcome());
        }
        Character target = context.targets().isEmpty() ? null : context.targets().get(0);
        AttackHit hit = hit(context, result, mono, steps, dice);
        if (context.request().options().contains(PARRYING)) {
            steps.addAll(hit.steps());
            steps.add("Парирование (с. 112): весь этот урон приходится в предмет. Клинок не портится, прочее ломается, если спасбросок выше 9, моно — выше 4");
            return AttackOutcome.hits(steps, List.of(), "Удар парирован", false);
        }
        return AttackOutcome.hits(steps, List.of(new AttackOutcome.TargetedHit(target, hit)), null, true);
    }

    private static AttackHit hit(AttackContext context, CheckResult result, boolean mono, List<String> intro, DiceRoller dice) {
        AttackRequest request = context.request();
        Item weapon = context.item();
        List<String> steps = new ArrayList<>();
        Cp2020Location location;
        if (request.hasChosenLocation()) {
            location = Cp2020Location.parse(request.location());
        } else {
            int roll = dice.d10();
            location = Cp2020Location.fromRoll(roll);
            steps.add("Бросок зоны: d10 = " + roll);
        }
        String damage = weapon != null ? weapon.getProperties().get("damage") : KICK.equals(request.mode()) ? KICK_DAMAGE : STRIKE_DAMAGE;
        Cp2020AttackRules.RolledDamage rolled = Cp2020AttackRules.rollText(damage, steps, dice);
        if (rolled == null) {
            return new AttackHit(location.getLabel(), null, steps, null);
        }
        int amount = rolled.amount();
        StatValue body = context.stats(context.attacker()).get(Cp2020StatsStrategy.BODY);
        int bonus = body == null ? 0 : Cp2020Rules.meleeDamageBonus(body.value());
        if (bonus != 0) {
            steps.add("Телосложение BODY " + body.value() + ": " + (bonus > 0 ? "+" : "") + bonus);
        }
        String skill = chosenSkill(context.attacker(), request, weapon == null ? BRAWL_SKILLS : WEAPON_SKILLS, move(request));
        if (Cp2020MartialArts.isStyle(skill)) {
            // С. 112: «a damage bonus equal to your current level of martial arts»
            int art = level(context.attacker(), skill);
            bonus += art;
            steps.add(skillName(skill) + ": урон +" + art);
        }
        // С. 25: значение с модификатором не меньше 1
        amount = Math.max(1, amount + bonus);
        if (mono && result.critical()) {
            amount *= 2;
            steps.add("Естественная 10: моно-лезвие удваивает урон → " + amount);
        }
        DamageRequest request1 = DamageRequest.builder()
                .amount(amount)
                .location(location.name())
                .damageType(damageType(weapon))
                .build();
        return new AttackHit(location.getLabel(), rolled.notation(), steps, request1);
    }

    private static String damageType(Item weapon) {
        if (weapon == null) {
            return Cp2020DamageType.NORMAL.name();
        }
        String edge = weapon.getProperties().get(Cp2020ItemTypes.EDGE);
        return edge != null ? edge : Cp2020AttackRules.damageType(weapon);
    }

    /** Приём нужен, чтобы лучшим считался навык с учётом его ключевой прибавки; у оружия приёмов нет */
    private static Move move(AttackRequest request) {
        if (KICK.equals(request.mode())) {
            return Move.KICK;
        }
        return STRIKE.equals(request.mode()) ? Move.STRIKE : null;
    }

    /**
     * Выбранный навык, а если не выбран — лучший из подходящих.
     * HOMEBREW: книга (с. 111) оставляет выбор навыка защиты мастеру, берём лучший: это ноль нажатий.
     *
     * @param request null — выбирает система: защита цели
     */
    private static String chosenSkill(Character character, AttackRequest request, List<String> skills, Move move) {
        String chosen = request == null ? null : request.skillCode();
        if (chosen != null && !chosen.isBlank()) {
            if (!skills.contains(chosen)) {
                throw new IllegalArgumentException("Этот навык не подходит: " + chosen);
            }
            return chosen;
        }
        return skills.stream()
                .max(Comparator.comparingInt((String code) -> level(character, code) + keyBonus(character, code, move)))
                .orElseThrow();
    }

    /** Приём ключевой только для того, кто знает стиль: без него прибавки нет */
    private static int keyBonus(Character character, String skill, Move move) {
        return move == null || level(character, skill) == 0 ? 0 : Cp2020MartialArts.keyBonus(skill, move);
    }

    private static int ref(Map<String, StatValue> stats) {
        StatValue ref = stats.get(Cp2020StatsStrategy.REF);
        return ref == null ? 0 : ref.value();
    }

    private static int level(Character character, String code) {
        return character.getSkills().stream()
                .filter(skill -> code.equals(skill.getSkillCode()))
                .mapToInt(CharacterSkill::getLevel)
                .findFirst()
                .orElse(0);
    }

    private static String skillName(String code) {
        return Cp2020Skills.CATALOG.stream()
                .filter(definition -> definition.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет такого навыка: " + code))
                .name();
    }
}
