package app.charka.system.cp2020;

import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.AbstractCounterStrategy;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.system.cp2020.Cp2020StatsView.StatLine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static app.charka.system.cp2020.Cp2020Rules.*;

/**
 * Характеристики персонажа Cyberpunk 2020 — полный набор из девяти статов в порядке книги правил:
 * INT, REF, TECH, COOL, ATTR, LUCK, MA, BODY, EMP. Плюс потеря человечности от киберимплантов
 * ({@code humanityLoss}): человечность = EMP × 10 − потеря, текущий EMP = человечность / 10 (вверх).
 * <p>
 * Текущие значения учитывают штрафы: REF/INT/COOL — от ран (уровень берётся из счётчика здоровья),
 * REF ещё от брони (из счётчика брони), EMP — от потери человечности. Производные величины (бег, прыжок, переноска, BTM) считаются
 * в {@link #describe(CounterContext)}. Значение самого счётчика не используется.
 */
@Component
public class Cp2020StatsStrategy extends AbstractCounterStrategy {

    public static final String TYPE = "CP2020_STATS";

    public static final String INT = "int";
    public static final String REF = "ref";
    public static final String TECH = "tech";
    public static final String COOL = "cool";
    public static final String ATTR = "attr";
    public static final String LUCK = "luck";
    public static final String MA = "ma";
    public static final String BODY = "body";
    public static final String EMP = "emp";
    public static final String HUMANITY_LOSS = "humanityLoss";

    public static final int DEFAULT_STAT = 6;
    /** При создании персонажа статы 2–10; выше 10 — только киберимпланты и т.п. */
    private static final int MIN_STAT = 1;
    private static final int MAX_STAT = 20;
    private static final int MAX_HUMANITY_LOSS = 200;

    /** Ключ → подпись, в порядке книги правил */
    private static final Map<String, String> STATS = new LinkedHashMap<>();

    static {
        STATS.put(INT, "INT");
        STATS.put(REF, "REF");
        STATS.put(TECH, "TECH");
        STATS.put(COOL, "COOL");
        STATS.put(ATTR, "ATTR");
        STATS.put(LUCK, "LUCK");
        STATS.put(MA, "MA");
        STATS.put(BODY, "BODY");
        STATS.put(EMP, "EMP");
    }

    @Override
    public String getCounterType() {
        return TYPE;
    }

    @Override
    public String getDisplayName() {
        return "Характеристики (Cyberpunk 2020)";
    }

    @Override
    public String getDefaultName() {
        return "Характеристики";
    }

    @Override
    public String getViewTemplate() {
        return "counters/types/cp2020-stats";
    }

    @Override
    public String getCreateFieldsTemplate() {
        return "counters/types/cp2020-stats";
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
        return 20;
    }

    @Override
    public Set<String> getSystems() {
        return Set.of(Cp2020System.CODE);
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
        STATS.keySet().forEach(key -> {
            Integer value = params == null ? null : params.get(key);
            counter.getAttributes().put(key, value == null ? DEFAULT_STAT : checkStat(key, value));
        });
        counter.getAttributes().put(HUMANITY_LOSS, 0);
    }

    @Override
    public void updateAttributes(Counter counter, Map<String, Integer> attributes) {
        attributes.forEach((key, value) -> {
            if (!STATS.containsKey(key) && !key.equals(HUMANITY_LOSS)) {
                throw new IllegalArgumentException("Неизвестная характеристика: " + key);
            }
            if (value == null) {
                return;
            }
            if (key.equals(HUMANITY_LOSS)) {
                if (value < 0 || value > MAX_HUMANITY_LOSS) {
                    throw new IllegalArgumentException("Потеря человечности должна быть от 0 до " + MAX_HUMANITY_LOSS);
                }
                counter.getAttributes().put(key, value);
            } else {
                counter.getAttributes().put(key, checkStat(key, value));
            }
        });
    }

    @Override
    public Cp2020StatsView describe(CounterContext context) {
        Counter counter = context.getCounter();
        int level = context.sibling(Cp2020HealthStrategy.TYPE)
                .map(health -> woundLevel(health.getValue() == null ? 0 : health.getValue()))
                .orElse(-1);
        int emp = counter.getAttribute(EMP, DEFAULT_STAT);
        int humanity = humanity(emp, counter.getAttribute(HUMANITY_LOSS, 0));
        // Книга (с. 67): EV вычитается из REF; порядок со штрафом от ран не указан, сначала EV
        int encumbrance = context.sibling(Cp2020ArmorStrategy.TYPE).map(Cp2020ArmorStrategy::encumbrance).orElse(0);

        List<StatLine> stats = new ArrayList<>();
        STATS.forEach((key, label) -> {
            int base = counter.getAttribute(key, DEFAULT_STAT);
            int effective = switch (key) {
                case REF -> effectiveRef(Math.max(base - encumbrance, 0), level);
                case INT, COOL -> effectiveIntOrCool(base, level);
                case EMP -> effectiveEmp(humanity);
                default -> base;
            };
            stats.add(new StatLine(key, label, base, effective));
        });

        int body = body(counter);
        int run = run(counter.getAttribute(MA, DEFAULT_STAT));
        return Cp2020StatsView.builder()
                .stats(stats)
                .btm(btm(body))
                .save(body)
                .run(run)
                .leap(leap(run))
                .carry(carry(body))
                .lift(lift(body))
                .humanity(humanity)
                .maxHumanity(emp * HUMANITY_PER_EMP)
                .humanityLoss(counter.getAttribute(HUMANITY_LOSS, 0))
                .build();
    }

    public static int body(Counter stats) {
        return stats.getAttribute(BODY, DEFAULT_STAT);
    }

    private static int checkStat(String key, int value) {
        if (value < MIN_STAT || value > MAX_STAT) {
            throw new IllegalArgumentException(STATS.get(key) + " должно быть от " + MIN_STAT + " до " + MAX_STAT);
        }
        return value;
    }
}
