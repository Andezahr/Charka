package app.charka.system.kosmozeki;

import app.charka.attack.AttackRules;
import app.charka.check.CheckResolver;
import app.charka.encounter.CombatCard;
import app.charka.encounter.Initiative;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.InspirationCounterStrategy;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcTemplate;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.CharacterRole;
import app.charka.system.Currency;
import app.charka.system.GameSystem;
import app.charka.system.ProfileField;
import app.charka.system.Reference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
