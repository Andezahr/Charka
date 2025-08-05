package app.charka.model.counters.strategy;

import org.springframework.stereotype.Component;

@Component
public class SimpleIntegerCounterStrategy extends AbstractCounterStrategy {

    @Override
    public String getCounterType() {
        return "SIMPLE_INTEGER";
    }

    @Override
    public String getNewValue(String currentValue, Object inputValue) {
        return String.valueOf((int) inputValue + Integer.parseInt(currentValue));
    }

    @Override
    public String getResetValue() {
        return "0";
    }

    @Override
    public boolean canHandle(String counterType) {
        return getCounterType().equals(counterType);
    }
}
