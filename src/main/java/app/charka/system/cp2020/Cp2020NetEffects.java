package app.charka.system.cp2020;

import app.charka.effect.EffectTemplate.DurationUnit;
import app.charka.effect.TimedEffects;
import app.charka.model.Character;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.skill.StatValue;

/**
 * Что делает попавшая антиперсональная программа (с. 140): по названию программы из справочника.
 * Программа с другим названием только попадает — эффект применяет мастер.
 */
final class Cp2020NetEffects {

    private Cp2020NetEffects() {
    }

    static String apply(String program, Character victim, Cp2020Netrunning.Net net, DiceRoller dice) {
        String name = program == null ? "" : program.strip();
        return switch (name) {
            case "Hellbolt" -> damage(victim, net, roll(1, 10, dice), "1D10");
            case "Sword" -> damage(victim, net, roll(1, 6, dice), "1D6");
            // С. 140: «heart attack in humans (2D10 wound damage)»
            case "Hellhound" -> damage(victim, net, roll(2, 10, dice), "2D10");
            case "Stun" -> timed(victim, "Замер в Сети", null, 0, "1d6", DurationUnit.ROUND, dice);
            case "Glue" -> timed(victim, "Приклеен в Сети", null, 0, "1d10", DurationUnit.ROUND, dice);
            case "Knockout" -> timed(victim, "Кома: выброшен из Сети", null, 0, "1d6", DurationUnit.HOUR, dice);
            case "Spazz" -> {
                StatValue ref = net.stats(victim).get(Cp2020StatsStrategy.REF);
                int current = ref == null ? Cp2020StatsStrategy.DEFAULT_STAT : ref.value();
                yield timed(victim, "Судороги: REF вдвое", Cp2020StatsStrategy.REF, -(current - current / 2), "1d6", DurationUnit.ROUND, dice);
            }
            case "Brainwipe", "Zombie", "Liche" -> loseInt(victim, dice);
            case "Jack Attack" -> victim.getName() + " не может выйти из Сети, пока программа не уничтожена";
            case "Firestarter" -> "Дека " + victim.getName() + " горит, как электрический стул: урон решает мастер";
            default -> "Эффект «" + name + "» применяет мастер";
        };
    }

    private static int roll(int count, int sides, DiceRoller dice) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += dice.roll(sides);
        }
        return total;
    }

    private static String damage(Character victim, Cp2020Netrunning.Net net, int amount, String dice) {
        String applied = net.damage(victim, amount);
        return victim.getName() + ": " + dice + " = " + amount + " урона" + (applied == null ? ", нанесите вручную" : ". " + applied);
    }

    private static String timed(Character victim, String name, String stat, int modifier, String duration, DurationUnit unit, DiceRoller dice) {
        var effect = TimedEffects.add(victim, name, stat, modifier, duration, unit, "Сетевая программа", dice);
        return victim.getName() + ": " + name + " на " + effect.getRemainingLabel();
    }

    /** Brainwipe, Zombie, Liche: −1D6 INT навсегда за попадание; на 0 — смерть (с. 140) */
    private static String loseInt(Character victim, DiceRoller dice) {
        int lost = dice.roll(6);
        return victim.getCounters().stream()
                .filter(counter -> Cp2020StatsStrategy.TYPE.equals(counter.getType()))
                .findFirst()
                .map(stats -> {
                    int before = stats.getAttribute(Cp2020StatsStrategy.INT, Cp2020StatsStrategy.DEFAULT_STAT);
                    int after = Math.max(0, before - lost);
                    stats.getAttributes().put(Cp2020StatsStrategy.INT, after);
                    return victim.getName() + ": INT −" + lost + " навсегда (" + before + " → " + after + ")"
                            + (after == 0 ? " — мозг выжжен, смерть" : "");
                })
                .orElse(victim.getName() + ": INT −" + lost + " навсегда, впишите вручную");
    }
}
