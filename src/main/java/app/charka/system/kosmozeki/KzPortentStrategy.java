package app.charka.system.kosmozeki;

import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.AbstractCounterStrategy;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.health.DiceRoller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Способность «Предсказание» (K-10, как Школа Прорицания в D&D): 3к12 бросаются во время длительного отдыха;
 * каждый можно один раз подставить вместо броска к12 (инициатива, драка).
 * <p>
 * attributes: {@code die_1}…{@code die_3} — отложенные кубики (нет ключа — использован), {@link #NEXT} — кубик, который
 * игрок выбрал для следующего броска к12. Подстановку во вражеский бросок мастер делает скрыто — её здесь нет (автор, 2026-11-07).
 */
@Component
@RequiredArgsConstructor
public class KzPortentStrategy extends AbstractCounterStrategy {

    public static final String TYPE = "KZ_PORTENT";
    static final String DIE_PREFIX = "die_";
    /** Значение кубика, подставляемого в следующий бросок к12 */
    static final String NEXT = "next";
    static final int DICE = 3;
    static final int SIDES = 12;

    private final DiceRoller dice;

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
        return "Способность: предсказание (3к12 после сна)";
    }

    @Override
    public String getDefaultName() {
        return "Предсказание";
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
        return 41;
    }

    @Override
    public void initialize(Counter counter, Map<String, Integer> params) {
        roll(counter, dice);
    }

    /** Длительный отдых: новые 3к12 вместо оставшихся */
    static void roll(Counter counter, DiceRoller dice) {
        counter.getAttributes().keySet().removeIf(key -> key.startsWith(DIE_PREFIX) || key.equals(NEXT));
        for (int i = 1; i <= DICE; i++) {
            counter.getAttributes().put(DIE_PREFIX + i, dice.roll(SIDES));
        }
        counter.setValue(DICE);
    }

    /** Отложенные кубики по порядку: номер → значение */
    static Map<Integer, Integer> dice(Counter counter) {
        Map<Integer, Integer> left = new LinkedHashMap<>();
        for (int i = 1; i <= DICE; i++) {
            int value = counter.getAttribute(DIE_PREFIX + i, 0);
            if (value > 0) {
                left.put(i, value);
            }
        }
        return left;
    }

    /** Взять отложенный на следующий бросок кубик */
    static int useNext(Counter counter) {
        int value = counter.getAttribute(NEXT, 0);
        counter.getAttributes().remove(NEXT);
        return value;
    }

    @Override
    public String getNewValue(String currentValue, Object inputValue) {
        return currentValue;
    }

    @Override
    public String getResetValue() {
        return String.valueOf(DICE);
    }

    @Override
    public void reset(Counter counter) {
        roll(counter, dice);
    }

    /**
     * Действия: {@code ROLL} — бросить 3к12 заново (длительный отдых), {@code USE_<n>} — подставить кубик n в следующий бросок к12,
     * {@code CANCEL} — вернуть отложенный.
     */
    @Override
    public String performAction(CounterContext context, String action) {
        Counter counter = context.getCounter();
        if ("ROLL".equals(action)) {
            roll(counter, dice);
            return "Предсказание: " + dice(counter).values();
        }
        if ("CANCEL".equals(action)) {
            int value = counter.getAttribute(NEXT, 0);
            if (value > 0) {
                int slot = 1;
                while (counter.getAttributes().containsKey(DIE_PREFIX + slot) && slot <= DICE) {
                    slot++;
                }
                counter.getAttributes().put(DIE_PREFIX + Math.min(slot, DICE), value);
                counter.getAttributes().remove(NEXT);
            }
            counter.setValue(dice(counter).size());
            return "Кубик возвращён";
        }
        if (action.startsWith("USE_")) {
            int slot = Integer.parseInt(action.substring(4));
            int value = counter.getAttribute(DIE_PREFIX + slot, 0);
            if (value <= 0) {
                throw new IllegalArgumentException("Этот кубик уже использован");
            }
            if (counter.getAttribute(NEXT, 0) > 0) {
                throw new IllegalArgumentException("Один кубик уже отложен на следующий бросок");
            }
            counter.getAttributes().remove(DIE_PREFIX + slot);
            counter.getAttributes().put(NEXT, value);
            counter.setValue(dice(counter).size());
            return "Следующий бросок к12 будет " + value;
        }
        throw new IllegalArgumentException("Неизвестное действие: " + action);
    }

    @Override
    public Object describe(CounterContext context) {
        Counter counter = context.getCounter();
        List<Map<String, Object>> dice = new ArrayList<>();
        dice(counter).forEach((slot, value) -> dice.add(Map.of("slot", slot, "value", value)));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("dice", dice);
        view.put("next", counter.getAttribute(NEXT, 0));
        view.put("hint", "Нажмите кубик — он заменит следующий бросок к12 (инициатива, драка). Сон бросает 3к12 заново.");
        return view;
    }
}
