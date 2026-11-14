package app.charka.system.kosmozeki;

import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Item;
import java.util.List;
import java.util.Map;
import app.charka.item.ItemRules;

/**
 * ARC-01: предметы: типы, справочник, действия, строка на листе, вживлённое — правила этой системы (вынесено из KzSystem).
 */
class KzItemRules implements ItemRules {

    @Override
    public List<ItemTypeDefinition> getItemTypes() {
        return KzItems.ALL;
    }

    @Override
    public String getWeaponItemType() {
        return KzItems.RANGED;
    }

    @Override
    public List<CatalogItem> getItemCatalog() {
        return KzItems.CATALOG;
    }

    @Override
    public List<ItemAction> getItemActions(Item item) {
        return KzItemActions.actions(item);
    }

    @Override
    public ItemSummary getItemSummary(Item item) {
        ItemSummary summary = KzItemActions.summary(item);
        return summary != null ? summary : ItemRules.super.getItemSummary(item);
    }

    @Override
    public ItemAttachments getItemAttachments(Item item) {
        return KzItemActions.attachments(item);
    }

    @Override
    public String performItemAction(Item item, String action, Map<String, String> params) {
        return KzItemActions.perform(item, action, params);
    }

    /** Копия NPC получает полный магазин */
    @Override
    public void prepareItemCopy(Item item) {
        if (KzItems.isRanged(item) && item.getProperties().containsKey(KzItems.CAPACITY)) {
            item.getProperties().put(KzItems.LOADED, item.getProperties().get(KzItems.CAPACITY));
        }
    }
}
