package app.charka.system.cp2020;

import app.charka.item.InstalledBlock;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.service.counter.HealthCounterService;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import app.charka.item.ItemRules;

/**
 * ARC-01: предметы: типы, справочник, действия, строка на листе, вживлённое — правила этой системы (вынесено из Cp2020System).
 */
class Cp2020ItemRules implements ItemRules {

    private static final List<CatalogItem> ITEM_CATALOG =
            Stream.of(Cp2020Weapons.CATALOG, Cp2020Magazines.CATALOG, Cp2020Ammo.CATALOG, Cp2020Armors.CATALOG, Cp2020Cyberware.CATALOG, Cp2020Chips.CATALOG, Cp2020Drugs.CATALOG, Cp2020Vehicles.CATALOG, Cp2020Netrunning.CATALOG).flatMap(List::stream).toList();

    private final Cp2020System system;
    private final HealthCounterService healthCounterService;
    private final DiceRoller dice;

    Cp2020ItemRules(Cp2020System system, HealthCounterService healthCounterService, DiceRoller dice) {
        this.system = system;
        this.healthCounterService = healthCounterService;
        this.dice = dice;
    }

    @Override
    public List<ItemTypeDefinition> getItemTypes() {
        return Cp2020ItemTypes.ALL;
    }

    @Override
    public String getWeaponItemType() {
        return Cp2020ItemTypes.WEAPON;
    }

    @Override
    public List<CatalogItem> getItemCatalog() {
        return ITEM_CATALOG;
    }

    @Override
    public List<ItemAction> getItemActions(Item item) {
        if (Cp2020Cyber.isCyber(item)) {
            return Cp2020Cyber.actions(item);
        }
        if (Cp2020Drugs.isDrug(item)) {
            return Cp2020Drugs.actions(item);
        }
        if (Cp2020Vehicles.isVehicle(item)) {
            return Cp2020Vehicles.actions(item);
        }
        if (Cp2020Netrunning.isDeck(item) || Cp2020Netrunning.isProgram(item)) {
            return Cp2020Netrunning.actions(item);
        }
        return Cp2020WornArmor.isArmor(item) ? Cp2020ArmorActions.actions(item) : Cp2020WeaponActions.actions(item);
    }

    @Override
    public ItemSummary getItemSummary(Item item) {
        if (Cp2020Vehicles.isVehicle(item)) {
            return Cp2020Vehicles.summary(item);
        }
        if (Cp2020Drugs.isDrug(item)) {
            return Cp2020Drugs.summary(item);
        }
        if (Cp2020Netrunning.isDeck(item) || Cp2020Netrunning.isProgram(item)) {
            return Cp2020Netrunning.summary(item);
        }
        ItemSummary summary = Cp2020ItemSheet.summary(item);
        return summary != null ? summary : ItemRules.super.getItemSummary(item);
    }

    @Override
    public ItemAttachments getItemAttachments(Item item) {
        return Cp2020ItemSheet.attachments(item);
    }

    @Override
    public List<CatalogItem> getSuggestedCatalog(Character character) {
        return Cp2020ItemSheet.suggested(character);
    }

    @Override
    public List<String> getItemNotes(Item item) {
        if (Cp2020Vehicles.isVehicle(item)) {
            return Cp2020Vehicles.notes(item);
        }
        if (Cp2020Netrunning.isDeck(item) || Cp2020Netrunning.isProgram(item)) {
            return Cp2020Netrunning.notes(item);
        }
        return Cp2020WornArmor.isArmor(item) ? Cp2020ArmorActions.notes(item) : Cp2020WeaponActions.notes(item);
    }

    @Override
    public String performItemAction(Item item, String action, Map<String, String> params) {
        if (Cp2020Cyber.isCyber(item)) {
            return Cp2020Cyber.perform(item, action, params, dice, healthCounterService);
        }
        if (Cp2020Drugs.isDrug(item)) {
            return Cp2020Drugs.perform(item, action, dice);
        }
        if (Cp2020Vehicles.isVehicle(item)) {
            return Cp2020Vehicles.perform(item, action, params, system.skills().getCurrentStats(item.getInventory().getCharacter()), system.getCheckResolver(), dice);
        }
        if (Cp2020Netrunning.isDeck(item) || Cp2020Netrunning.isProgram(item)) {
            return Cp2020Netrunning.perform(item, action, params, system.skills().getCurrentStats(item.getInventory().getCharacter()), system.net(), system.getCheckResolver(), dice);
        }
        return Cp2020WornArmor.isArmor(item) ? Cp2020ArmorActions.perform(item, action) : Cp2020WeaponActions.perform(item, action, params);
    }

    /** HOMEBREW: копия получает снаряжение как новое: целая броня, рабочее оружие, полный магазин. В книге копий нет */
    @Override
    public void prepareItemCopy(Item item) {
        item.getProperties().keySet().removeIf(key -> key.startsWith("wear_") || key.equals(Cp2020ItemTypes.JAMMED));
        if (Cp2020ItemTypes.WEAPON.equals(item.getType()) && item.getProperties().containsKey(Cp2020ItemTypes.LOADED)) {
            String capacity = item.getProperties().getOrDefault(Cp2020ItemTypes.INSERTED, item.getProperties().get(Cp2020ItemTypes.SHOTS));
            if (capacity != null) {
                item.getProperties().put(Cp2020ItemTypes.LOADED, capacity);
            }
        }
    }

    @Override
    public java.util.Set<String> getItemReferenceKeys() {
        return java.util.Set.of(Cp2020Netrunning.LOADED_IN, Cp2020ItemTypes.LIMB_HOST);
    }

    @Override
    public String performInstalledAction(Character character, String action, Map<String, String> params) {
        return Cp2020Cyber.therapy(character, action, params);
    }

    @Override
    public InstalledBlock getInstalled(Character character) {
        return Cp2020Cyber.block(character);
    }
}
