package app.charka.system.kosmozeki;

import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.AbstractCounterStrategy;
import app.charka.model.counters.strategy.CounterContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Способность «Сложность вдвое» (K-10): раз за бой снизить сложность попадания вдвое — как выгоднее.
 * Значение — сколько раз ещё можно в этом бою; {@code max} — сколько за бой. Новый бой восстанавливает.
 */
@Component
public class KzHalveStrategy extends AbstractCounterStrategy {

    public static final String TYPE = "KZ_HALVE";
    static final String MAX = "max";

    @Override
    public String getCounterType() {
        return TYPE;
    }

    @Override
    public Set<String> getSystems() {
        return Set.of(KzSystem.CODE);
    }

    @Override
    public String getDisplayName() {
        return "Способность: сложность вдвое (раз за бой)";
    }

    @Override
    public String getDefaultName() {
        return "Сложность вдвое";
    }

    @Override
    public String getViewTemplate() {
        return "counters/types/kz-ability";
    }

    @Override
    public boolean usesInitialValue() {
        return false;
    }

    @Override
    public boolean isUniquePerCharacter() {
        return true;
    }

    @Override
    public int getDisplayOrder() {
        return 40;
    }

    static int max(Counter counter) {
        return Math.max(1, counter.getAttribute(MAX, 1));
    }

    @Override
    public void initialize(Counter counter, Map<String, Integer> params) {
        counter.setValue(1);
    }

    @Override
    public String getNewValue(String currentValue, Object inputValue) {
        return String.valueOf(Math.max(0, Integer.parseInt(currentValue) + (int) inputValue));
    }

    @Override
    public String getResetValue() {
        return "1";
    }

    @Override
    public void reset(Counter counter) {
        counter.setValue(max(counter));
    }

    @Override
    public void prepareCopy(Counter counter) {
        reset(counter);
    }

    @Override
    public void updateAttributes(Counter counter, Map<String, Integer> attributes) {
        attributes.forEach((key, value) -> {
            if (!MAX.equals(key) || value == null || value < 1) {
                throw new IllegalArgumentException("Раз за бой — от 1");
            }
            counter.getAttributes().put(MAX, value);
        });
    }

    @Override
    public Object describe(CounterContext context) {
        Counter counter = context.getCounter();
        return Map.of("left", KzAbilities.charges(counter), "max", max(counter),
                "hint", "В диалоге атаки — флажок «Сложность вдвое». Новый бой возвращает.");
    }

    @Override
    public String performAction(CounterContext context, String action) {
        if (!"RESTORE".equals(action)) {
            throw new IllegalArgumentException("Неизвестное действие: " + action);
        }
        reset(context.getCounter());
        return "Способность снова доступна";
    }
}
