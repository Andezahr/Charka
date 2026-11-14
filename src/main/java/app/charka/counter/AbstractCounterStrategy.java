package app.charka.counter;

public abstract class AbstractCounterStrategy implements CounterStrategy {
    @Override
    public boolean canHandle(String counterType) {
        return getCounterType().equals(counterType);
    }
}