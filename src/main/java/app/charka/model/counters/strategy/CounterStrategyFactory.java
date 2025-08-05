package app.charka.model.counters.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Component
public class CounterStrategyFactory {

    private final Map<String, CounterStrategy> strategies;

    public CounterStrategyFactory(java.util.List<CounterStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(CounterStrategy::getCounterType, s -> s));
    }

    public CounterStrategy getStrategy(String counterType) {
        CounterStrategy strategy = strategies.get(counterType);
        if (strategy == null) {
            throw new NoSuchElementException("Strategy not found for type: " + counterType);
        }
        return strategy;
    }
}
