package app.charka.system.cp2020;

import app.charka.attack.AttackForm;
import app.charka.attack.AttackHit;
import app.charka.attack.AttackRequest;
import app.charka.attack.AttackRules;
import app.charka.check.CheckRequest;
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

/**
 * Дальняя атака одиночным выстрелом (FNFF, с. 99, 105): REF + навык оружия + WA + модификаторы + 1d10 против сложности по дальности.
 * Попадание: зона 1d10 или выбранная, урон оружия. Режимы огня — I-07, заклинивание — I-08, ближний бой — C-06, метание и взрывчатка — I-09.
 */
final class Cp2020AttackRules implements AttackRules {

    static final int POINT_BLANK = 10;
    static final int CLOSE = 15;
    static final int MEDIUM = 20;
    static final int LONG = 25;
    static final int EXTREME = 30;

    private static final int POINT_BLANK_METERS = 1;

    /** Урон с делением: «1D6/3» */
    private static final Pattern DAMAGE = Pattern.compile("^(\\d*[dD]\\d+(?:[+-]\\d+)?)(?:/(\\d+))?$");

    private static final List<String> EXOTIC_SKILLS = List.of("archery", "handgun", "submachinegun", "rifle", "heavy_weapons");

    @Override
    public AttackForm form(Item weapon) {
        if (!Cp2020ItemTypes.WEAPON.equals(weapon.getType()) || "MELEE".equals(category(weapon)) || number(weapon, Cp2020ItemTypes.SHOTS) < 1) {
            return null;
        }
        Integer range = range(weapon);
        String unavailable = range == null
                ? "У оружия не указана дальность: задайте её в правке предмета"
                : number(weapon, Cp2020ItemTypes.LOADED) == 0 ? Cp2020WeaponActions.emptyReason(weapon) : null;
        List<Option> locations = Arrays.stream(Cp2020Location.values())
                .map(location -> new Option(location.name(), location.getLabel()))
                .toList();
        return new AttackForm(weapon.getProperties().get("damage"), range, unavailable,
                skillCodes(weapon).stream().map(code -> new Option(code, skillName(code))).toList(),
                locations, Cp2020AttackModifiers.sections());
    }

    @Override
    public CheckRequest toHit(Character attacker, Item weapon, AttackRequest request, Map<String, StatValue> stats) {
        AttackForm form = form(weapon);
        if (form.unavailable() != null) {
            throw new IllegalArgumentException(form.unavailable());
        }
        int hitNumber = hitNumber(request.distance(), form.range());
        String skillCode = request.skillCode() == null || request.skillCode().isBlank()
                ? skillCodes(weapon).get(0)
                : request.skillCode();
        if (!skillCodes(weapon).contains(skillCode)) {
            throw new IllegalArgumentException("Этот навык не подходит оружию: " + skillCode);
        }

        List<CheckRequest.Part> parts = new ArrayList<>();
        StatValue ref = stats.get(Cp2020StatsStrategy.REF);
        parts.add(new CheckRequest.Part("REF", ref == null ? 0 : ref.value()));
        parts.add(new CheckRequest.Part(skillName(skillCode), skillLevel(attacker, skillCode)));
        parts.add(new CheckRequest.Part("WA", number(weapon, "accuracy")));
        parts.addAll(Cp2020AttackModifiers.parts(request.options(), hitNumber));
        if (request.hasChosenLocation()) {
            Cp2020Location location = Cp2020Location.parse(request.location());
            parts.add(new CheckRequest.Part("Прицельный выстрел: " + location.getLabel(), Cp2020AttackModifiers.AIMED_SHOT));
        }
        if (request.modifier() != 0) {
            parts.add(new CheckRequest.Part("Мод.", request.modifier()));
        }
        return CheckRequest.ofParts("Атака: " + weapon.getName(), parts, hitNumber, skillCode);
    }

    @Override
    public String spend(Item weapon) {
        return Cp2020WeaponActions.fireOne(weapon);
    }

    @Override
    public List<AttackHit> hits(Item weapon, AttackRequest request, DiceRoller dice) {
        List<String> steps = new ArrayList<>();
        Cp2020Location location;
        if (request.hasChosenLocation()) {
            location = Cp2020Location.parse(request.location());
        } else {
            int roll = dice.d10();
            location = Cp2020Location.fromRoll(roll);
            steps.add("Бросок зоны: d10 = " + roll);
        }

        String damage = weapon.getProperties().get("damage");
        Matcher parsed = damage == null ? null : DAMAGE.matcher(damage.strip());
        if (parsed == null || !parsed.matches()) {
            steps.add("Урон оружия «" + (damage == null ? "не указан" : damage) + "» не бросить: определяет мастер");
            return List.of(new AttackHit(location.getLabel(), null, steps, null));
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

        boolean armorPiercing = weapon.getProperties().containsKey(Cp2020ItemTypes.ARMOR_PIERCING);
        DamageRequest damageRequest = DamageRequest.builder()
                .amount(amount)
                .location(location.name())
                .damageType(armorPiercing ? Cp2020DamageType.ARMOR_PIERCING.name() : Cp2020DamageType.NORMAL.name())
                .armorWear(Math.max(1, number(weapon, Cp2020ItemTypes.ARMOR_WEAR)))
                .build();
        return List.of(new AttackHit(location.getLabel(), notation, steps, damageRequest));
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
    private static Integer range(Item weapon) {
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

    private static String category(Item weapon) {
        return weapon.getProperties().get("category");
    }

    private static int number(Item item, String key) {
        String value = item.getProperties().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
