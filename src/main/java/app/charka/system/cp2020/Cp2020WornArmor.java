package app.charka.system.cp2020;

import app.charka.model.Character;
import app.charka.model.Item;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Надетая броня персонажа: предметы типа «Броня» со свойством {@code worn}. Значение свойства — порядок надевания, поэтому первый надетый слой внутренний.
 * Слой считается по зоне: шлем и куртка не лежат друг на друге.
 * HOMEBREW: книга говорит о 3 слоях «at any one time», без зон; лишний штраф за слои считается по самой закрытой зоне, не по каждой; предмет с SP 0 (одежда) слоем не считается.
 */
final class Cp2020WornArmor {

    private Cp2020WornArmor() {
    }

    /** Весь надетый предмет: от внутреннего слоя к внешнему */
    static List<Item> worn(Character character) {
        if (character == null) {
            return List.of();
        }
        return character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(Cp2020WornArmor::isWorn)
                .sorted(Comparator.comparingInt((ToIntFunction<Item>) item -> number(item, Cp2020ItemTypes.WORN)))
                .toList();
    }

    /** Надетые слои зоны: от внутреннего к внешнему */
    static List<Item> layers(Character character, Cp2020Location location) {
        return worn(character).stream().filter(item -> sp(item) > 0 && covers(item, location)).toList();
    }

    static boolean isArmor(Item item) {
        return Cp2020ItemTypes.ARMOR.equals(item.getType());
    }

    static boolean isWorn(Item item) {
        return isArmor(item) && item.getProperties().containsKey(Cp2020ItemTypes.WORN);
    }

    static boolean covers(Item item, Cp2020Location location) {
        return item.getProperties().containsKey(Cp2020ItemTypes.zoneKey(location));
    }

    static boolean isHard(Item item) {
        return Cp2020ItemTypes.HARD.equals(item.getProperties().get(Cp2020ItemTypes.HARDNESS));
    }

    static int sp(Item item) {
        return number(item, Cp2020ItemTypes.ARMOR_SP);
    }

    static int encumbrance(Item item) {
        return number(item, Cp2020ItemTypes.ENCUMBRANCE);
    }

    static int wear(Item item, Cp2020Location location) {
        return number(item, Cp2020ItemTypes.wearKey(location));
    }

    /** Износ на одно пробитие (FNFF, Staged Penetration): SP зоны −1 */
    static void addWear(Item item, Cp2020Location location) {
        item.getProperties().put(Cp2020ItemTypes.wearKey(location), String.valueOf(wear(item, location) + 1));
    }

    static boolean isDamaged(Item item) {
        return Arrays.stream(Cp2020Location.values()).anyMatch(location -> wear(item, location) > 0);
    }

    static void repair(Item item) {
        for (Cp2020Location location : Cp2020Location.values()) {
            item.getProperties().remove(Cp2020ItemTypes.wearKey(location));
        }
    }

    private static int number(Item item, String key) {
        String value = item.getProperties().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
