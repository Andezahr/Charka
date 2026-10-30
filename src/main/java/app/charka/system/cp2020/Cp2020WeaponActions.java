package app.charka.system.cp2020;

import app.charka.item.ItemAction;
import app.charka.item.ItemField.Option;
import app.charka.model.Inventory;
import app.charka.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Магазин оружия: выстрел, очередь, перезарядка из запаса патронов того же калибра в инвентарях персонажа.
 * Книга (с. 60): магазин — «# Shots», за раунд не больше ROF. Перезарядка заменяет магазин (FNFF: одно действие), поэтому патроны
 * другого вида в магазине не смешиваются: остаток возвращается в запас.
 * Попадание и урон — I-05, режимы огня — I-07; пока очередь только расходует патроны.
 */
final class Cp2020WeaponActions {

    static final String FIRE = "FIRE";
    static final String BURST = "BURST";
    static final String RELOAD = "RELOAD";

    private static final int BURST_ROUNDS = 3;

    private Cp2020WeaponActions() {
    }

    /**
     * Действия оружия с магазином; у холодного и одноразового его нет.
     */
    static List<ItemAction> actions(Item weapon) {
        int shots = number(weapon, Cp2020ItemTypes.SHOTS);
        if (!Cp2020ItemTypes.WEAPON.equals(weapon.getType()) || shots < 1) {
            return List.of();
        }
        int loaded = number(weapon, Cp2020ItemTypes.LOADED);
        int rof = number(weapon, Cp2020ItemTypes.RATE_OF_FIRE);
        String empty = loaded == 0 ? "Магазин пуст" : null;

        List<ItemAction> actions = new ArrayList<>();
        actions.add(ItemAction.simple(FIRE, "Выстрел", empty));
        if (rof > 1) {
            int max = Math.min(rof, loaded);
            actions.add(ItemAction.withAmount(BURST, "Очередь", empty, Math.min(BURST_ROUNDS, max), max));
        }
        actions.add(reloadAction(weapon, shots, loaded));
        return actions;
    }

    private static ItemAction reloadAction(Item weapon, int shots, int loaded) {
        String caliber = caliber(weapon);
        if (caliber == null) {
            return ItemAction.simple(RELOAD, "Перезарядить", "У оружия не указан патрон: заряд задаётся в правке предмета");
        }
        boolean loadedAp = isArmorPiercing(weapon);
        List<Option> sources = stacks(weapon)
                .filter(stack -> loaded < shots || isArmorPiercing(stack) != loadedAp)
                .map(stack -> new Option(String.valueOf(stack.getId()), sourceLabel(stack)))
                .toList();
        String unavailable = sources.isEmpty()
                ? (loaded == shots ? "Магазин полон" : "Нет патронов «" + caliber + "» в инвентарях персонажа: добавьте их через «Из справочника»")
                : null;
        return ItemAction.withSources(RELOAD, "Перезарядить", unavailable, sources);
    }

    /**
     * @return итог для показа игроку
     * @throws IllegalArgumentException если действие сейчас невозможно
     */
    static String perform(Item weapon, String action, Map<String, String> params) {
        if (actions(weapon).stream().noneMatch(known -> known.code().equals(action))) {
            throw new IllegalArgumentException("Неизвестное действие с предметом: " + action);
        }
        return switch (action) {
            case FIRE -> fire(weapon, 1);
            case BURST -> fire(weapon, amount(params));
            default -> reload(weapon, params.get("source"));
        };
    }

    private static String fire(Item weapon, int rounds) {
        int loaded = number(weapon, Cp2020ItemTypes.LOADED);
        int rof = Math.max(1, number(weapon, Cp2020ItemTypes.RATE_OF_FIRE));
        if (rounds < 1) {
            throw new IllegalArgumentException("Число патронов должно быть не меньше 1");
        }
        if (rounds > rof) {
            throw new IllegalArgumentException("За раунд можно выпустить не больше " + rof + " (ROF)");
        }
        if (rounds > loaded) {
            throw new IllegalArgumentException(loaded == 0 ? "Магазин пуст" : "В магазине только " + loaded);
        }
        setLoaded(weapon, loaded - rounds, isArmorPiercing(weapon));
        return weapon.getName() + ": выпущено " + rounds + ", заряжено " + (loaded - rounds) + " из " + number(weapon, Cp2020ItemTypes.SHOTS);
    }

    private static String reload(Item weapon, String sourceId) {
        Item stack = stacks(weapon)
                .filter(candidate -> String.valueOf(candidate.getId()).equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Выберите запас патронов того же калибра"));
        int shots = number(weapon, Cp2020ItemTypes.SHOTS);
        int loaded = number(weapon, Cp2020ItemTypes.LOADED);
        boolean armorPiercing = isArmorPiercing(stack);

        int returned = 0;
        if (loaded > 0 && isArmorPiercing(weapon) != armorPiercing) {
            returned = loaded;
            returnToStock(weapon, returned);
            loaded = 0;
        }
        int taken = Math.min(shots - loaded, stack.getQuantity());
        if (taken == 0) {
            throw new IllegalArgumentException("Магазин полон");
        }
        takeFromStack(stack, taken);
        setLoaded(weapon, loaded + taken, armorPiercing);

        return weapon.getName() + ": заряжено " + (loaded + taken) + " из " + shots + (armorPiercing ? " AP" : "")
                + ", из запаса взято " + taken
                + (returned > 0 ? ", в запас возвращено " + returned : "");
    }

    /** Остаток другого вида: в такой же запас рядом с оружием, а если его нет — в новый */
    private static void returnToStock(Item weapon, int rounds) {
        boolean armorPiercing = isArmorPiercing(weapon);
        Inventory inventory = weapon.getInventory();
        Optional<Item> same = inventory.getContent().stream()
                .filter(item -> isStackFor(item, caliber(weapon)) && isArmorPiercing(item) == armorPiercing)
                .findFirst();
        if (same.isPresent()) {
            same.get().setQuantity(quantity(same.get()) + rounds);
            return;
        }
        Item stack = new Item();
        stack.setName("Патроны " + caliber(weapon) + (armorPiercing ? " AP" : ""));
        stack.setQuantity(rounds);
        stack.setCost(0);
        stack.setType(Cp2020ItemTypes.AMMO);
        stack.getProperties().put(Cp2020ItemTypes.CALIBER, caliber(weapon));
        if (armorPiercing) {
            stack.getProperties().put(Cp2020ItemTypes.ARMOR_PIERCING, "1");
        }
        stack.setInventory(inventory);
        inventory.getContent().add(stack);
    }

    /** Пустая коробка исчезает: в форме предмета количество не меньше 1 */
    private static void takeFromStack(Item stack, int rounds) {
        int left = stack.getQuantity() - rounds;
        stack.setQuantity(left);
        if (left == 0) {
            // по ссылке: equals сущностей Lombok ходит по связям с инвентарём
            stack.getInventory().getContent().removeIf(item -> item == stack);
        }
    }

    private static void setLoaded(Item weapon, int loaded, boolean armorPiercing) {
        weapon.getProperties().put(Cp2020ItemTypes.LOADED, String.valueOf(loaded));
        if (armorPiercing && loaded > 0) {
            weapon.getProperties().put(Cp2020ItemTypes.ARMOR_PIERCING, "1");
        } else {
            weapon.getProperties().remove(Cp2020ItemTypes.ARMOR_PIERCING);
        }
    }

    /** Запасы патронов этого калибра во всех инвентарях владельца; сначала те, что лежат рядом с оружием */
    private static Stream<Item> stacks(Item weapon) {
        String caliber = caliber(weapon);
        if (caliber == null) {
            return Stream.empty();
        }
        Inventory own = weapon.getInventory();
        Stream<Inventory> others = own.getCharacter().getInventories().stream().filter(inventory -> inventory != own);
        return Stream.concat(Stream.of(own), others)
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(item -> isStackFor(item, caliber) && quantity(item) > 0);
    }

    private static boolean isStackFor(Item item, String caliber) {
        return Cp2020ItemTypes.AMMO.equals(item.getType()) && caliber.equalsIgnoreCase(caliber(item));
    }

    private static String sourceLabel(Item stack) {
        return caliber(stack) + (isArmorPiercing(stack) ? " AP" : "") + " × " + stack.getQuantity() + " (" + stack.getInventory().getName() + ")";
    }

    private static int amount(Map<String, String> params) {
        try {
            return Integer.parseInt(params.getOrDefault("amount", "").strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Укажите, сколько патронов выпустить");
        }
    }

    private static String caliber(Item item) {
        String caliber = item.getProperties().get(Cp2020ItemTypes.CALIBER);
        return caliber == null || caliber.isBlank() ? null : caliber.strip();
    }

    private static boolean isArmorPiercing(Item item) {
        return item.getProperties().containsKey(Cp2020ItemTypes.ARMOR_PIERCING);
    }

    private static int quantity(Item item) {
        return item.getQuantity() == null ? 0 : item.getQuantity();
    }

    private static int number(Item item, String key) {
        String value = item.getProperties().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
