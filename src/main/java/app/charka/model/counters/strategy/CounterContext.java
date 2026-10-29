package app.charka.model.counters.strategy;

import app.charka.model.counters.Counter;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Счётчик вместе с остальными счётчиками того же персонажа.
 * Нужен стратегиям, которые зависят друг от друга: здоровье CP2020 читает BODY из характеристик
 * и броню из счётчика брони (см. {@link CounterStrategy#getRequiredCounterTypes()}).
 */
@Getter
public class CounterContext {

    private final Counter counter;
    /** Все счётчики персонажа, включая сам counter */
    private final List<Counter> siblings;

    public CounterContext(Counter counter, List<Counter> siblings) {
        this.counter = counter;
        this.siblings = siblings;
    }

    /** Первый (по id) счётчик персонажа указанного типа */
    public Optional<Counter> sibling(String type) {
        return siblings.stream()
                .filter(c -> type.equals(c.getType()))
                .min(Comparator.comparing(Counter::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    public Counter requireSibling(String type, String displayName) {
        return sibling(type).orElseThrow(() ->
                new IllegalStateException("У персонажа нет счётчика «" + displayName + "»"));
    }
}
