package app.charka.effect;

import app.charka.dice.DiceExpression;
import app.charka.model.Character;
import app.charka.model.TimedEffect;
import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Временные эффекты на персонаже: сумма правок стата и ход времени. Без состояния: всё лежит в {@link Character#getEffects()},
 * поэтому правила системы и трекер боя зовут их без сервиса.
 */
public final class TimedEffects {

    private TimedEffects() {
    }

    /** Сумма правок стата от действующих эффектов */
    public static int modifier(Character character, String stat) {
        if (character == null || character.getEffects() == null) {
            return 0;
        }
        return character.getEffects().stream()
                .filter(effect -> stat.equals(effect.getStat()))
                .mapToInt(TimedEffect::getModifier)
                .sum();
    }

    /**
     * Новый эффект на персонаже. Срок — кубики или число; минуты переводятся в раунды.
     *
     * @throws IllegalArgumentException если срок не разобран или меньше 1
     */
    public static TimedEffect add(Character character, String name, String stat, int modifier, String duration,
                                  EffectTemplate.DurationUnit unit, String note, DiceRoller dice) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Назовите эффект");
        }
        int amount = rollDuration(duration, dice);
        if (amount < 1) {
            throw new IllegalArgumentException("Срок эффекта — от 1");
        }
        TimedEffect effect = new TimedEffect();
        effect.setName(name.strip());
        effect.setStat(stat == null || stat.isBlank() ? null : stat);
        effect.setModifier(effect.getStat() == null ? 0 : modifier);
        effect.setNote(note == null || note.isBlank() ? null : note.strip());
        switch (unit) {
            case ROUND -> {
                effect.setUnit(TimedEffect.Unit.ROUND);
                effect.setRemaining(amount);
            }
            case MINUTE -> {
                effect.setUnit(TimedEffect.Unit.ROUND);
                effect.setRemaining(amount * TimedEffect.ROUNDS_PER_MINUTE);
            }
            case HOUR -> {
                effect.setUnit(TimedEffect.Unit.HOUR);
                effect.setRemaining(amount);
            }
            case DAY -> {
                effect.setUnit(TimedEffect.Unit.DAY);
                effect.setRemaining(amount);
            }
        }
        effect.setCharacter(character);
        character.getEffects().add(effect);
        return effect;
    }

    public static TimedEffect add(Character character, EffectTemplate template, DiceRoller dice) {
        return add(character, template.name(), template.stat(), template.modifier(), template.duration(), template.unit(), template.note(), dice);
    }

    /**
     * Раунды боя прошли: эффекты в раундах убывают.
     *
     * @return названия закончившихся эффектов
     */
    public static List<String> passRounds(Character character, int rounds) {
        List<String> ended = new ArrayList<>();
        for (Iterator<TimedEffect> it = character.getEffects().iterator(); it.hasNext(); ) {
            TimedEffect effect = it.next();
            if (effect.getUnit() == TimedEffect.Unit.ROUND) {
                effect.setRemaining(effect.getRemaining() - rounds);
                if (effect.getRemaining() <= 0) {
                    ended.add(effect.getName());
                    it.remove();
                }
            }
        }
        return ended;
    }

    /**
     * Прошли дни: раунды и часы кончились (самые долгие — несколько часов), дни убывают.
     *
     * @return названия закончившихся эффектов
     */
    public static List<String> passDays(Character character, int days) {
        List<String> ended = new ArrayList<>();
        if (days <= 0) {
            return ended;
        }
        for (Iterator<TimedEffect> it = character.getEffects().iterator(); it.hasNext(); ) {
            TimedEffect effect = it.next();
            if (effect.getUnit() == TimedEffect.Unit.DAY) {
                effect.setRemaining(effect.getRemaining() - days);
            } else {
                effect.setRemaining(0);
            }
            if (effect.getRemaining() <= 0) {
                ended.add(effect.getName());
                it.remove();
            }
        }
        return ended;
    }

    private static int rollDuration(String duration, DiceRoller dice) {
        String text = duration == null ? "" : duration.strip();
        if (text.matches("[0-9]+")) {
            return Integer.parseInt(text);
        }
        DiceExpression expression = DiceExpression.parse(text);
        int total = expression.modifier();
        for (int i = 0; i < expression.count(); i++) {
            total += dice.roll(expression.sides());
        }
        return total;
    }
}
