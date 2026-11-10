package app.charka.system.kosmozeki;

import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.List;
import java.util.Optional;

/**
 * Особые способности Космозэков (K-10) и общие помощники: счётчики персонажа, к12 с подставным кубиком.
 */
public final class KzAbilities {

    private KzAbilities() {
    }

    public static Optional<Counter> counter(Character character, String type) {
        if (character == null || character.getCounters() == null) {
            return Optional.empty();
        }
        return character.getCounters().stream().filter(counter -> type.equals(counter.getType())).findFirst();
    }

    public static Optional<Counter> bodyCounter(Character character) {
        return counter(character, KzBodyStrategy.TYPE);
    }

    public static Optional<Counter> halveCounter(Character character) {
        return counter(character, KzHalveStrategy.TYPE);
    }

    /** Сколько раз ещё можно в этом бою */
    public static int charges(Counter counter) {
        return counter.getValue() == null ? 0 : counter.getValue();
    }

    /**
     * Потратить «Сложность вдвое».
     *
     * @throws IllegalArgumentException если способности нет или она уже потрачена в этом бою
     */
    static void spendHalve(Character character) {
        Counter counter = halveCounter(character)
                .filter(found -> charges(found) > 0)
                .orElseThrow(() -> new IllegalArgumentException("«Сложность вдвое» в этом бою уже потрачена"));
        counter.setValue(charges(counter) - 1);
    }

    /** Новый бой: способности «раз за бой» восстанавливаются */
    static void restoreForFight(Character character) {
        halveCounter(character).ifPresent(counter -> counter.setValue(KzHalveStrategy.max(counter)));
    }

    /**
     * Бросок к12 персонажа: если он отложил подставной кубик предсказания на следующий бросок — берётся он.
     *
     * @param steps куда записать, как получено
     */
    static int d12(Character character, DiceRoller dice, List<String> steps, String who) {
        Optional<Counter> portent = counter(character, KzPortentStrategy.TYPE)
                .filter(counter -> counter.getAttribute(KzPortentStrategy.NEXT, 0) > 0);
        if (portent.isPresent()) {
            int value = KzPortentStrategy.useNext(portent.get());
            steps.add(who + ": к12 = " + value + " (предсказание)");
            return value;
        }
        int value = dice.roll(KzAttackRules.BRAWL_DIE);
        steps.add(who + ": к12 = " + value);
        return value;
    }
}
