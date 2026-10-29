package app.charka.model.counters.strategy.health.cp2020;

import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.AbstractCounterStrategy;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.health.cp2020.Cp2020ArmorView.ArmorLine;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Броня CP2020 по зонам попадания. Для каждой зоны хранится полный SP надетой брони ({@code max_<ЗОНА>})
 * и текущий SP ({@code sp_<ЗОНА>}), который падает на 1 при каждом пробитии. Ремонт возвращает полный SP.
 * Значение счётчика не используется.
 */
@Component
public class Cp2020ArmorStrategy extends AbstractCounterStrategy {

    public static final String TYPE = "CP2020_ARMOR";
    public static final String SP_PREFIX = "sp_";
    public static final String MAX_PREFIX = "max_";
    public static final String ACTION_REPAIR = "REPAIR";

    private static final int MAX_SP = 60;

    @Override
    public String getCounterType() {
        return TYPE;
    }

    @Override
    public String getDisplayName() {
        return "Броня (Cyberpunk 2020)";
    }

    @Override
    public String getDefaultName() {
        return "Броня";
    }

    @Override
    public String getViewTemplate() {
        return "counters/types/cp2020-armor";
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
        return 30;
    }

    @Override
    public String getNewValue(String currentValue, Object inputValue) {
        return currentValue;
    }

    @Override
    public String getResetValue() {
        return "0";
    }

    @Override
    public void initialize(Counter counter, Map<String, Integer> params) {
        counter.setValue(0);
        for (Cp2020Location location : Cp2020Location.values()) {
            counter.getAttributes().putIfAbsent(spKey(location), 0);
            counter.getAttributes().putIfAbsent(maxKey(location), 0);
        }
    }

    /** Сброс = ремонт */
    @Override
    public void reset(Counter counter) {
        repair(counter);
    }

    /**
     * Ключи {@code sp_<ЗОНА>}: надели новую броню — меняется и полный, и текущий SP.
     */
    @Override
    public void updateAttributes(Counter counter, Map<String, Integer> attributes) {
        attributes.forEach((key, value) -> {
            Cp2020Location location = key.startsWith(SP_PREFIX)
                    ? Cp2020Location.parse(key.substring(SP_PREFIX.length()))
                    : null;
            if (location == null) {
                throw new IllegalArgumentException("Неизвестный параметр брони: " + key);
            }
            if (value == null) {
                return;
            }
            if (value < 0 || value > MAX_SP) {
                throw new IllegalArgumentException("SP должен быть от 0 до " + MAX_SP);
            }
            counter.getAttributes().put(spKey(location), value);
            counter.getAttributes().put(maxKey(location), value);
        });
    }

    @Override
    public String performAction(CounterContext context, String action) {
        if (!ACTION_REPAIR.equalsIgnoreCase(action)) {
            throw new IllegalArgumentException("Неизвестное действие с бронёй: " + action);
        }
        repair(context.getCounter());
        return "«" + context.getCounter().getName() + "» отремонтирована — SP восстановлен";
    }

    @Override
    public Cp2020ArmorView describe(CounterContext context) {
        Counter counter = context.getCounter();
        List<ArmorLine> lines = Arrays.stream(Cp2020Location.values())
                .map(l -> new ArmorLine(l.name(), l.getLabel(), l.getShortLabel(), l.getRolls(), sp(counter, l), maxSp(counter, l)))
                .toList();
        return new Cp2020ArmorView(lines, lines.stream().anyMatch(ArmorLine::isWorn));
    }

    public int sp(Counter armor, Cp2020Location location) {
        return armor.getAttribute(spKey(location), 0);
    }

    public int maxSp(Counter armor, Cp2020Location location) {
        return Math.max(armor.getAttribute(maxKey(location), 0), sp(armor, location));
    }

    /**
     * Износ при пробитии: SP зоны −1 (не ниже 0).
     *
     * @return новый SP
     */
    public int wear(Counter armor, Cp2020Location location) {
        int sp = Math.max(0, sp(armor, location) - 1);
        armor.getAttributes().put(spKey(location), sp);
        return sp;
    }

    public void repair(Counter armor) {
        for (Cp2020Location location : Cp2020Location.values()) {
            armor.getAttributes().put(spKey(location), maxSp(armor, location));
        }
    }

    public static String spKey(Cp2020Location location) {
        return SP_PREFIX + location.name();
    }

    private static String maxKey(Cp2020Location location) {
        return MAX_PREFIX + location.name();
    }
}
