package app.charka.system.cp2020;

import app.charka.item.ItemAction;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Надеть, снять и починить предмет брони. Ограничения слоёв — FNFF, с. 101: не больше трёх, жёсткий только один.
 */
final class Cp2020ArmorActions {

    static final String WEAR = "WEAR";
    static final String TAKE_OFF = "TAKE_OFF";
    static final String REPAIR = "REPAIR";

    private Cp2020ArmorActions() {
    }

    static List<ItemAction> actions(Item item) {
        List<ItemAction> actions = new ArrayList<>();
        actions.add(Cp2020WornArmor.isWorn(item)
                ? ItemAction.simple(TAKE_OFF, "Снять", null)
                : ItemAction.simple(WEAR, "Надеть", wearBlockedReason(item)));
        if (Cp2020WornArmor.isDamaged(item)) {
            actions.add(ItemAction.simple(REPAIR, "Починить", null));
        }
        return actions;
    }

    static List<String> notes(Item item) {
        List<String> notes = new ArrayList<>();
        if (Cp2020WornArmor.isWorn(item)) {
            notes.add("Надета");
        }
        if (Cp2020WornArmor.isDamaged(item)) {
            notes.add("Износ: " + Arrays.stream(Cp2020Location.values())
                    .filter(location -> Cp2020WornArmor.wear(item, location) > 0)
                    .map(location -> location.getShortLabel() + " −" + Cp2020WornArmor.wear(item, location))
                    .collect(Collectors.joining(", ")));
        }
        return notes;
    }

    /**
     * @return итог для показа игроку
     * @throws IllegalArgumentException если действие сейчас невозможно
     */
    static String perform(Item item, String action) {
        if (actions(item).stream().noneMatch(known -> known.code().equals(action))) {
            throw new IllegalArgumentException("Неизвестное действие с предметом: " + action);
        }
        return switch (action) {
            case WEAR -> wear(item);
            case TAKE_OFF -> takeOff(item);
            default -> repair(item);
        };
    }

    private static String wear(Item item) {
        String blocked = wearBlockedReason(item);
        if (blocked != null) {
            throw new IllegalArgumentException(blocked);
        }
        int outermost = Cp2020WornArmor.worn(owner(item)).stream()
                .mapToInt(worn -> Integer.parseInt(worn.getProperties().get(Cp2020ItemTypes.WORN)))
                .max()
                .orElse(0);
        item.getProperties().put(Cp2020ItemTypes.WORN, String.valueOf(outermost + 1));
        return item.getName() + ": надета";
    }

    private static String takeOff(Item item) {
        item.getProperties().remove(Cp2020ItemTypes.WORN);
        return item.getName() + ": снята";
    }

    private static String repair(Item item) {
        Cp2020WornArmor.repair(item);
        return item.getName() + ": отремонтирована, SP восстановлен";
    }

    /** Причина, почему предмет нельзя надеть; null — можно */
    static String wearBlockedReason(Item item) {
        Character owner = owner(item);
        Counter armor = owner.getCounters().stream()
                .filter(counter -> Cp2020ArmorStrategy.TYPE.equals(counter.getType()))
                .findFirst()
                .orElse(null);
        if (armor == null) {
            return "У персонажа нет счётчика брони: создайте его на вкладке «Счётчики»";
        }
        if (Cp2020WornArmor.sp(item) == 0) {
            return null;
        }
        for (Cp2020Location location : Cp2020Location.values()) {
            if (!Cp2020WornArmor.covers(item, location)) {
                continue;
            }
            List<Item> layers = Cp2020WornArmor.layers(owner, location);
            int count = layers.size() + (Cp2020ArmorStrategy.ownMaxSp(armor, location) > 0 ? 1 : 0);
            if (count >= Cp2020ArmorRules.MAX_LAYERS) {
                return "На зоне «" + location.getLabel() + "» уже " + count + " слоя: больше " + Cp2020ArmorRules.MAX_LAYERS + " нельзя";
            }
            if (Cp2020WornArmor.isHard(item) && layers.stream().filter(Cp2020WornArmor::isHard).count() >= Cp2020ArmorRules.MAX_HARD_LAYERS) {
                return "На зоне «" + location.getLabel() + "» уже есть жёсткая броня: можно только один такой слой";
            }
        }
        return null;
    }

    private static Character owner(Item item) {
        return item.getInventory().getCharacter();
    }
}
