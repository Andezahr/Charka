package app.charka.system.cp2020;

import app.charka.attack.AreaDamage;
import app.charka.attack.AttackHit;
import app.charka.attack.AttackRequest;
import app.charka.dice.DiceExpression;
import app.charka.dice.DiceResult;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Урон по площади (с. 99, 108–111): зона, общий бросок урона, отклонение при промахе по таблице гранат, попадание по цели.
 */
final class Cp2020AreaAttacks {

    private static final String DAMAGE_TYPE = "damageType";
    private static final String WHOLE_BODY = "wholeBody";
    private static final String ARMOR_WEAR = "armorWear";
    private static final String LOCATION = "location";
    private static final String COVER_SP = "coverSp";

    private Cp2020AreaAttacks() {
    }

    /**
     * @param steps  шаги расчёта
     * @param meters на сколько метров отклонился заряд
     */
    record Scatter(List<String> steps, int meters) {
    }

    static boolean isArea(Item weapon) {
        return weapon.getProperties().containsKey(Cp2020ItemTypes.AREA) || "SHT".equals(Cp2020AttackRules.category(weapon));
    }

    static AreaDamage areaDamage(Item weapon, AttackRequest request, DiceRoller dice) {
        Cp2020AttackRules.requireMode(weapon, request);
        int radius = radius(weapon, request);
        List<String> steps = new ArrayList<>();
        steps.add("Зона поражения: " + radius + " м");
        Cp2020AttackRules.RolledDamage rolled = Cp2020AttackRules.rollDamage(weapon, request, steps, dice);
        String expression = rolled != null ? rolled.notation() : weapon.getProperties().getOrDefault("damage", "эффект");
        Map<String, String> data = new HashMap<>();
        String effect = weapon.getProperties().get(Cp2020ItemTypes.EFFECT);
        if (effect != null) {
            data.put(Cp2020ItemTypes.EFFECT, effect);
        }
        data.put(DAMAGE_TYPE, Cp2020Effects.damageType(effect, Cp2020AttackRules.damageType(weapon)));
        data.put(WHOLE_BODY, String.valueOf(Cp2020AttackRules.isExplosive(weapon) || Cp2020Effects.GAS.equals(effect)));
        data.put(COVER_SP, String.valueOf(Cp2020Cover.sp(request.options())));
        data.put(ARMOR_WEAR, String.valueOf(Cp2020AttackRules.armorWear(weapon)));
        return new AreaDamage(weapon.getName(), expression, rolled == null ? null : rolled.amount(), radius, data, steps);
    }

    /**
     * Таблица гранат (с. 99, 108): 1D10 — направление, ещё 1D10 — метров от цели.
     */
    static Scatter scatter(AreaDamage blast, DiceRoller dice) {
        int direction = dice.d10();
        int meters = dice.d10();
        List<String> steps = new ArrayList<>();
        steps.add("Отклонение: d10 = " + direction + " (" + direction(direction) + "), d10 = " + meters + " м");
        steps.add(meters <= blast.radius() ? "Цель всё ещё в зоне " + blast.radius() + " м" : "Цель вне зоны " + blast.radius() + " м");
        return new Scatter(steps, meters);
    }

    static AttackHit hit(AreaDamage blast, DiceRoller dice) {
        List<String> steps = new ArrayList<>();
        String effect = blast.data().get(Cp2020ItemTypes.EFFECT);
        if (blast.amount() == null) {
            if (Cp2020Effects.isKnown(effect)) {
                return new AttackHit("зона", null, steps, null, Cp2020Effects.save(effect), Cp2020Effects.lingering(effect, blast.expression(), Map.of()));
            }
            steps.add("Эффект «" + blast.expression() + "»: применяет мастер");
            return new AttackHit("зона", null, steps, null);
        }
        Cp2020Location location;
        if (Boolean.parseBoolean(blast.data().get(WHOLE_BODY))) {
            // HOMEBREW: книга (с. 111) пишет «по всему телу», но не говорит о броне; считаем как торс, без ×2 в голову
            location = Cp2020Location.TORSO;
            steps.add("По всему телу: броня торса");
        } else {
            int roll = dice.d10();
            location = Cp2020Location.fromRoll(roll);
            steps.add("Бросок зоны: d10 = " + roll);
        }
        DamageRequest damage = DamageRequest.builder()
                .amount(blast.amount())
                .location(location.name())
                .damageType(blast.data().get(DAMAGE_TYPE))
                .extras(Map.of(Cp2020HealthStrategy.ARMOR_WEAR, Integer.parseInt(blast.data().getOrDefault(ARMOR_WEAR, "1")),
                        Cp2020HealthStrategy.COVER_SP, Integer.parseInt(blast.data().getOrDefault(COVER_SP, "0")),
                        Cp2020HealthStrategy.POISON_SAVE, Cp2020Effects.GAS.equals(effect) ? 1 : 0))
                .build();
        // HOMEBREW: книга не говорит, куда приходится догорание; туда же, куда первый урон
        Map<String, String> burn = Map.of(LOCATION, location.name(), DAMAGE_TYPE, blast.data().get(DAMAGE_TYPE));
        return new AttackHit(location.getLabel(), blast.expression(), steps, damage, Cp2020Effects.save(effect), Cp2020Effects.lingering(effect, blast.expression(), burn));
    }

    /** Догорание: новый бросок по той же зоне и с той же бронёй */
    static AttackHit effectHit(String expression, Map<String, String> data, DiceRoller dice) {
        DiceExpression parsed = DiceExpression.parse(expression);
        DiceResult result = DiceResult.of(parsed, IntStream.range(0, parsed.count()).map(i -> dice.roll(parsed.sides())).boxed().toList());
        Cp2020Location location = Cp2020Location.parse(data.get(LOCATION));
        DamageRequest damage = DamageRequest.builder()
                .amount(result.total())
                .location(location.name())
                .damageType(data.get(DAMAGE_TYPE))
                .extras(Map.of(Cp2020HealthStrategy.POISON_SAVE, Cp2020DamageType.BYPASS.name().equals(data.get(DAMAGE_TYPE)) ? 1 : 0))
                .build();
        return new AttackHit(location.getLabel(), expression, List.of("Урон " + expression + ": " + result.details()), damage);
    }

    /** Схема направлений (с. 99): 1 ближе всех к стрелку, снизу вверх ряды 2 3 4, 5 цель 6, 7 8 9, вверху 10 */
    private static String direction(int roll) {
        return switch (roll) {
            case 1 -> "к стрелку";
            case 2 -> "к стрелку, левее";
            case 3 -> "ближе к стрелку";
            case 4 -> "к стрелку, правее";
            case 5 -> "левее цели";
            case 6 -> "правее цели";
            case 7 -> "за цель, левее";
            case 8 -> "за цель";
            case 9 -> "за цель, правее";
            default -> "далеко за цель";
        };
    }

    /**
     * Зона взрывчатки умножается на число единиц (с. 111); пятно дробовика зависит от дальности (с. 109),
     * а пятна автодробовика ложатся через метр (с. 109).
     */
    private static int radius(Item weapon, AttackRequest request) {
        if (weapon.getProperties().containsKey(Cp2020ItemTypes.AREA)) {
            return Cp2020AttackRules.number(weapon, Cp2020ItemTypes.AREA) * Cp2020AttackRules.units(weapon, request);
        }
        int hitNumber = Cp2020AttackRules.hitNumber(request.distance(), Cp2020AttackRules.range(weapon));
        int pattern = hitNumber <= Cp2020AttackRules.CLOSE ? 1 : hitNumber == Cp2020AttackRules.MEDIUM ? 2 : 3;
        return Cp2020AttackRules.PATTERNS.equals(Cp2020AttackRules.mode(request)) ? pattern + request.rounds() - 1 : pattern;
    }
}
