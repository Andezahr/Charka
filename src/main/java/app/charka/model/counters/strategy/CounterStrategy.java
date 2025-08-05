package app.charka.model.counters.strategy;

public interface CounterStrategy {
    String getCounterType();
    String getNewValue(String currentValue, Object inputValue);
    String  getResetValue();
    boolean canHandle(String counterType);
}


