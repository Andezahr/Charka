package app.charka.system.cp2020;

import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.AbstractCounterStrategy;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.system.cp2020.Cp2020ArmorView.ArmorLine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Броня CP2020 по зонам попадания. SP зоны складывается из надетых предметов ({@link Cp2020WornArmor}) и своего слоя счётчика:
 * брони, заданной вручную, без предмета. У своего слоя хранится полный SP ({@code max_<ЗОНА>}) и текущий ({@code sp_<ЗОНА>}).
 * Каждое пробитие снимает с зоны 1 SP. Ремонт возвращает полный SP. Значение счётчика не используется.
 * HOMEBREW: свой слой считается внутренним, мягким и без EV.
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
        for (Cp2020Location location : Cp2020Location.values()) {
            counter.getAttributes().putIfAbsent(spKey(location), 0);
            counter.getAttributes().putIfAbsent(maxKey(location), 0);
        }
    }

    /** Копия начинает в целой броне */
    @Override
    public void prepareCopy(Counter counter) {
        reset(counter);
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
                .map(l -> new ArmorLine(l.name(), l.getLabel(), l.getShortLabel(), l.getRolls(), sp(counter, l), maxSp(counter, l), ownMaxSp(counter, l)))
                .toList();
        List<String> items = Cp2020WornArmor.worn(counter.getCharacter()).stream()
                .map(item -> item.getName() + " (SP " + Cp2020WornArmor.sp(item) + ")")
                .toList();
        return new Cp2020ArmorView(lines, lines.stream().anyMatch(ArmorLine::isWorn), items, encumbrance(counter));
    }

    /**
     * Текущий SP зоны: полный SP всех слоёв минус их износ, не ниже 0.
     */
    public int sp(Counter armor, Cp2020Location location) {
        List<Item> layers = Cp2020WornArmor.layers(armor.getCharacter(), location);
        int worn = ownMaxSp(armor, location) - ownSp(armor, location)
                + layers.stream().mapToInt(item -> Cp2020WornArmor.wear(item, location)).sum();
        return Math.max(0, maxSp(armor, location) - worn);
    }

    /**
     * Есть ли в зоне жёсткий слой. HOMEBREW: книга (с. 110) не говорит о смешанных слоях; один жёсткий слой — и зона защищена как жёсткая. Свой слой счётчика считается мягким.
     */
    public boolean isHard(Counter armor, Cp2020Location location) {
        return Cp2020WornArmor.layers(armor.getCharacter(), location).stream().anyMatch(Cp2020WornArmor::isHard);
    }

    /**
     * Полный SP зоны: свой слой счётчика внутри, надетые предметы поверх, по таблице пропорциональной брони.
     */
    public int maxSp(Counter armor, Cp2020Location location) {
        List<Integer> layers = new ArrayList<>();
        layers.add(ownMaxSp(armor, location));
        Cp2020WornArmor.layers(armor.getCharacter(), location).forEach(item -> layers.add(Cp2020WornArmor.sp(item)));
        return Cp2020ArmorRules.combine(layers);
    }

    /**
     * Износ при пробитии: SP зоны −1 (не ниже 0). HOMEBREW: книга не говорит, какой слой изнашивается, — внешний, поэтому износ уходит вместе с ним.
     *
     * @return новый SP
     */
    public int wear(Counter armor, Cp2020Location location) {
        if (sp(armor, location) == 0) {
            return 0;
        }
        List<Item> layers = Cp2020WornArmor.layers(armor.getCharacter(), location);
        if (layers.isEmpty()) {
            armor.getAttributes().put(spKey(location), ownSp(armor, location) - 1);
        } else {
            Cp2020WornArmor.addWear(layers.get(layers.size() - 1), location);
        }
        return sp(armor, location);
    }

    public void repair(Counter armor) {
        for (Cp2020Location location : Cp2020Location.values()) {
            armor.getAttributes().put(spKey(location), ownMaxSp(armor, location));
        }
        Cp2020WornArmor.worn(armor.getCharacter()).forEach(Cp2020WornArmor::repair);
    }

    /**
     * Штраф брони к REF (FNFF, с. 101): сумма EV надетого плюс лишний штраф за слои по самой закрытой зоне.
     * Свой слой счётчика считается слоем, но своего EV у него нет.
     */
    public static int encumbrance(Counter armor) {
        List<Item> worn = Cp2020WornArmor.worn(armor.getCharacter());
        int layers = Arrays.stream(Cp2020Location.values())
                .mapToInt(location -> Cp2020WornArmor.layers(armor.getCharacter(), location).size() + (ownMaxSp(armor, location) > 0 ? 1 : 0))
                .max()
                .orElse(0);
        return worn.stream().mapToInt(Cp2020WornArmor::encumbrance).sum() + Cp2020ArmorRules.layerPenalty(layers);
    }

    /** Полный SP своего слоя счётчика: броня, заданная вручную, без предмета */
    static int ownMaxSp(Counter armor, Cp2020Location location) {
        return Math.max(armor.getAttribute(maxKey(location), 0), ownSp(armor, location));
    }

    private static int ownSp(Counter armor, Cp2020Location location) {
        return armor.getAttribute(spKey(location), 0);
    }

    public static String spKey(Cp2020Location location) {
        return SP_PREFIX + location.name();
    }

    private static String maxKey(Cp2020Location location) {
        return MAX_PREFIX + location.name();
    }
}
