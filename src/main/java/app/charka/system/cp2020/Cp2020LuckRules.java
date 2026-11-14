package app.charka.system.cp2020;

import app.charka.model.Character;
import app.charka.model.counters.Counter;
import java.util.Optional;
import app.charka.check.LuckRules;

/**
 * ARC-01: удача: сколько осталось, потратить на бросок, вернуть к новой сессии — правила этой системы (вынесено из Cp2020System).
 */
class Cp2020LuckRules implements LuckRules {

    /** Удача (с. 26): любую часть LUCK прибавляют к важному броску, объявив до него; к концу сессии возвращается */
    @Override
    public int getLuck(Character character) {
        return statsCounter(character)
                .map(stats -> Math.max(0, stats.getAttribute(Cp2020StatsStrategy.LUCK, Cp2020StatsStrategy.DEFAULT_STAT)
                        - stats.getAttribute(Cp2020StatsStrategy.LUCK_SPENT, 0)))
                .orElse(0);
    }

    @Override
    public void spendLuck(Character character, int points) {
        if (points <= 0) {
            return;
        }
        int left = getLuck(character);
        if (points > left) {
            throw new IllegalArgumentException("Удачи осталось " + left + ": вернётся в новой сессии");
        }
        statsCounter(character).ifPresent(stats -> stats.getAttributes().put(Cp2020StatsStrategy.LUCK_SPENT,
                stats.getAttribute(Cp2020StatsStrategy.LUCK_SPENT, 0) + points));
    }

    @Override
    public boolean hasLuck() {
        return true;
    }

    @Override
    public void restoreLuck(Character character) {
        statsCounter(character).ifPresent(stats -> stats.getAttributes().remove(Cp2020StatsStrategy.LUCK_SPENT));
    }

    private static java.util.Optional<app.charka.model.counters.Counter> statsCounter(Character character) {
        return character.getCounters().stream()
                .filter(counter -> Cp2020StatsStrategy.TYPE.equals(counter.getType()))
                .findFirst();
    }
}
