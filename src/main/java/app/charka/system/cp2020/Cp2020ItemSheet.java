package app.charka.system.cp2020;

import app.charka.item.CatalogItem;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemChip;
import app.charka.item.ItemField.Option;
import app.charka.item.ItemSummary;
import app.charka.model.Character;
import app.charka.model.Item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Как предметы CP2020 выглядят на листе: краткая строка и магазины с патронами под оружием.
 * Правила здесь не решаются: что куда подходит и что происходит при нажатии — {@link Cp2020WeaponActions}.
 */
final class Cp2020ItemSheet {

    private static final String CURRENCY = "eb";

    private Cp2020ItemSheet() {
    }

    /** @return null, если для этого типа особой строки нет */
    static ItemSummary summary(Item item) {
        return switch (item.getType()) {
            case Cp2020ItemTypes.WEAPON -> weaponSummary(item);
            case Cp2020ItemTypes.ARMOR -> armorSummary(item);
            case Cp2020ItemTypes.MAGAZINE -> new ItemSummary(charge(item), caliberUnlessNamed(item));
            case Cp2020ItemTypes.AMMO -> new ItemSummary(null, caliberUnlessNamed(item));
            case Cp2020ItemTypes.CYBER -> new ItemSummary(null, List.of("−" + item.getProperties().getOrDefault(Cp2020ItemTypes.HUMANITY_COST, "0") + " человечности"));
            default -> null;
        };
    }

    /** «Патроны 10mm  10mm» — повтор; свой патрон показываем, только если название его не называет */
    private static List<String> caliberUnlessNamed(Item item) {
        String caliber = Cp2020WeaponActions.caliber(item);
        return caliber == null || item.getName().contains(caliber) ? List.of() : List.of(caliber);
    }

    private static ItemSummary weaponSummary(Item weapon) {
        List<String> lines = new ArrayList<>();
        String damage = Cp2020AttackRules.damageOf(weapon);
        if (damage != null) {
            lines.add(damage);
        }
        int rof = Cp2020WeaponActions.number(weapon, Cp2020ItemTypes.RATE_OF_FIRE);
        if (rof > 1) {
            lines.add("ROF " + rof);
        }
        String charge = weapon.getProperties().get(Cp2020ItemTypes.CHARGE);
        if (charge != null) {
            lines.add(charge);
        }
        String area = weapon.getProperties().get(Cp2020ItemTypes.AREA);
        if (area != null) {
            lines.add("зона " + area + " м");
        }
        if (Cp2020WeaponActions.number(weapon, Cp2020ItemTypes.SHOTS) < 1) {
            return new ItemSummary(null, lines);
        }
        boolean noMagazine = Cp2020WeaponActions.usesMagazines(weapon) && !Cp2020WeaponActions.hasMagazine(weapon);
        return new ItemSummary(noMagazine ? "нет магазина" : charge(weapon), lines);
    }

    private static ItemSummary armorSummary(Item armor) {
        List<String> lines = new ArrayList<>();
        lines.add(Arrays.stream(Cp2020Location.values())
                .filter(location -> Cp2020WornArmor.covers(armor, location))
                .map(Cp2020Location::getShortLabel)
                .collect(Collectors.joining(", ")));
        int encumbrance = Cp2020WeaponActions.number(armor, Cp2020ItemTypes.ENCUMBRANCE);
        if (encumbrance > 0) {
            lines.add("EV " + encumbrance);
        }
        return new ItemSummary("SP " + Cp2020WeaponActions.number(armor, Cp2020ItemTypes.ARMOR_SP), lines);
    }

    private static String charge(Item holder) {
        return Cp2020WeaponActions.number(holder, Cp2020ItemTypes.LOADED) + "/" + Cp2020WeaponActions.capacity(holder)
                + (Cp2020WeaponActions.isArmorPiercing(holder) ? " AP" : "");
    }

    /**
     * Магазины нажимаются прямо под оружием: снаряжённый встаёт в оружие, пустой снаряжается. Сверху самые полные.
     */
    static ItemAttachments attachments(Item weapon) {
        if (!Cp2020ItemTypes.WEAPON.equals(weapon.getType()) || Cp2020WeaponActions.number(weapon, Cp2020ItemTypes.SHOTS) < 1
                || Cp2020WeaponActions.caliber(weapon) == null) {
            return ItemAttachments.NONE;
        }
        boolean usesMagazines = Cp2020WeaponActions.usesMagazines(weapon);
        List<Item> magazines = usesMagazines ? Cp2020WeaponActions.fittingMagazines(weapon).toList() : List.of();
        List<Item> stacks = Cp2020WeaponActions.stacks(weapon).toList();

        List<ItemAttachments.Group> groups = new ArrayList<>();
        if (usesMagazines) {
            groups.add(new ItemAttachments.Group("магазины", magazineChips(weapon, magazines, stacks)));
        }
        groups.add(new ItemAttachments.Group("патроны", stackChips(weapon, stacks)));

        return new ItemAttachments(groups, Stream.concat(magazines.stream(), stacks.stream()).toList(),
                usesMagazines ? "Магазины и патроны" : "Патроны", warning(weapon, magazines, stacks));
    }

    private static List<ItemChip> magazineChips(Item weapon, List<Item> magazines, List<Item> stacks) {
        List<Option> sources = stacks.stream()
                .map(stack -> new Option(String.valueOf(stack.getId()), Cp2020WeaponActions.caliber(stack)
                        + (Cp2020WeaponActions.isArmorPiercing(stack) ? " AP" : "") + " × " + stack.getQuantity() + placeNote(weapon, stack)))
                .toList();
        List<ItemChip> chips = new ArrayList<>();
        for (Item magazine : magazines) {
            chips.add(magazineChip(weapon, magazine, sources));
        }
        long empty = magazines.stream().filter(magazine -> Cp2020WeaponActions.number(magazine, Cp2020ItemTypes.LOADED) == 0).count();
        if (empty > 1 && !sources.isEmpty()) {
            chips.add(ItemChip.action("Снарядить все", "Снарядить все пустые магазины", weapon.getId(), Cp2020WeaponActions.LOAD_ALL, sources));
        }
        chips.add(ItemChip.action("＋", "Добавить пустой магазин", weapon.getId(), Cp2020WeaponActions.ADD_MAGAZINE, List.of()));
        return chips;
    }

    private static ItemChip magazineChip(Item weapon, Item magazine, List<Option> sources) {
        String label = charge(magazine);
        String place = placeNote(weapon, magazine);
        if (Cp2020WeaponActions.number(magazine, Cp2020ItemTypes.LOADED) > 0) {
            return ItemChip.action(label, "Вставить в оружие" + place, weapon.getId(), Cp2020WeaponActions.RELOAD,
                    List.of(new Option(String.valueOf(magazine.getId()), label)));
        }
        return sources.isEmpty()
                ? ItemChip.unavailable(label, "Пустой, и снарядить нечем" + place)
                : ItemChip.action(label, "Снарядить" + place, magazine.getId(), Cp2020WeaponActions.LOAD, sources);
    }

    private static List<ItemChip> stackChips(Item weapon, List<Item> stacks) {
        List<ItemChip> chips = new ArrayList<>();
        for (Item stack : stacks) {
            String label = stack.getQuantity() + (Cp2020WeaponActions.isArmorPiercing(stack) ? " AP" : "");
            String place = placeNote(weapon, stack);
            chips.add(canTakeFrom(weapon, stack)
                    ? ItemChip.action(label, "Зарядить этими патронами" + place, weapon.getId(), Cp2020WeaponActions.RELOAD,
                    List.of(new Option(String.valueOf(stack.getId()), label)))
                    : ItemChip.info(label, "Патронов в запасе" + place));
        }
        List<Option> entries = Cp2020Ammo.CATALOG.stream()
                .filter(entry -> Cp2020WeaponActions.caliber(weapon).equalsIgnoreCase(entry.properties().get(Cp2020ItemTypes.CALIBER)))
                .map(entry -> new Option(entry.key(), packLabel(entry)))
                .toList();
        if (!entries.isEmpty()) {
            chips.add(ItemChip.fromCatalog("＋", "Добавить патроны", entries));
        }
        return chips;
    }

    /** С теми же условиями, что у перезарядки: патроны россыпью идут в магазин, который уже в оружии */
    private static boolean canTakeFrom(Item weapon, Item stack) {
        int loaded = Cp2020WeaponActions.number(weapon, Cp2020ItemTypes.LOADED);
        int capacity = Cp2020WeaponActions.capacity(weapon);
        if (Cp2020WeaponActions.usesMagazines(weapon)) {
            return Cp2020WeaponActions.hasMagazine(weapon) && loaded < capacity;
        }
        return loaded < capacity || Cp2020WeaponActions.isArmorPiercing(stack) != Cp2020WeaponActions.isArmorPiercing(weapon);
    }

    private static String packLabel(CatalogItem entry) {
        return entry.packSize() + " шт."
                + (entry.properties().containsKey(Cp2020ItemTypes.ARMOR_PIERCING) ? " AP" : "")
                + ", " + entry.cost() + " " + CURRENCY;
    }

    private static String placeNote(Item weapon, Item other) {
        return other.getInventory() == weapon.getInventory() ? "" : " (" + other.getInventory().getName() + ")";
    }

    /** Предупреждаем, только когда зарядить действительно нечем: полному оружию запас не нужен */
    private static String warning(Item weapon, List<Item> magazines, List<Item> stacks) {
        boolean full = Cp2020WeaponActions.hasMagazine(weapon)
                && Cp2020WeaponActions.number(weapon, Cp2020ItemTypes.LOADED) >= Cp2020WeaponActions.capacity(weapon);
        boolean anyLoaded = magazines.stream().anyMatch(magazine -> Cp2020WeaponActions.number(magazine, Cp2020ItemTypes.LOADED) > 0);
        return full || anyLoaded || !stacks.isEmpty() ? null : "Нечем перезарядить: нет ни снаряжённых магазинов, ни патронов";
    }

    /**
     * Патроны и магазины к оружию персонажа: по патрону, внутри него от меньшего магазина к большему.
     */
    static List<CatalogItem> suggested(Character character) {
        List<Item> weapons = character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(item -> Cp2020ItemTypes.WEAPON.equals(item.getType()) && Cp2020WeaponActions.caliber(item) != null
                        && Cp2020WeaponActions.number(item, Cp2020ItemTypes.SHOTS) >= 1)
                .toList();
        return Stream.concat(Cp2020Magazines.CATALOG.stream(), Cp2020Ammo.CATALOG.stream())
                .filter(entry -> weapons.stream().anyMatch(weapon -> suits(weapon, entry)))
                .sorted(Comparator.comparing((CatalogItem entry) -> entry.properties().get(Cp2020ItemTypes.CALIBER))
                        .thenComparing(entry -> Integer.parseInt(entry.properties().getOrDefault(Cp2020ItemTypes.SHOTS, "0"))))
                .toList();
    }

    private static boolean suits(Item weapon, CatalogItem entry) {
        if (!Cp2020WeaponActions.caliber(weapon).equalsIgnoreCase(entry.properties().get(Cp2020ItemTypes.CALIBER))) {
            return false;
        }
        if (Cp2020ItemTypes.AMMO.equals(entry.typeCode())) {
            return true;
        }
        return Cp2020WeaponActions.usesMagazines(weapon) && Cp2020MagazineRules.fits(Cp2020WeaponActions.number(weapon, Cp2020ItemTypes.SHOTS),
                Integer.parseInt(entry.properties().get(Cp2020ItemTypes.SHOTS)));
    }
}
