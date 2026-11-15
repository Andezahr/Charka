package app.charka.item;

import app.charka.item.InstalledBlock;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Character;
import app.charka.model.Item;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Предметы: типы, справочник, действия, строка на листе, вживлённое
 * <p>
 * ARC-01: часть правил системы ({@code GameSystem#items()}); каждый метод по умолчанию — «у системы этого нет».
 */
public interface ItemRules {

    /** Система без этой части */
    ItemRules NONE = new ItemRules() {
    };

    /**
     * Типы предметов системы; обычный предмет есть всегда.
     */
    default List<ItemTypeDefinition> getItemTypes() {
        return List.of(ItemTypeDefinition.GENERIC);
    }

    /**
     * UX-14: тип предмета «оружие» — вкладка окна «Добавить», которую открывает «＋ Оружие» на пустой карточке оружия;
     * null — открыть последнюю вкладку.
     */
    default String getWeaponItemType() {
        return null;
    }

    default Optional<ItemTypeDefinition> findItemType(String code) {
        return getItemTypes().stream().filter(type -> type.code().equals(code)).findFirst();
    }

    /**
     * Справочник готовых предметов; пусто — у системы его нет.
     */
    default List<CatalogItem> getItemCatalog() {
        return List.of();
    }

    /**
     * Действия над предметом на листе персонажа; пусто — у предмета их нет. Вызывать в транзакции.
     */
    default List<ItemAction> getItemActions(Item item) {
        return List.of();
    }

    /**
     * Краткая строка предмета на листе. Вызывать в транзакции.
     */
    default ItemSummary getItemSummary(Item item) {
        return findItemType(item.getType())
                .map(type -> ItemSummary.allFields(type, item.getProperties()))
                .orElse(new ItemSummary(null, List.of()));
    }

    /**
     * Что показывается вместе с предметом, а не своими строками. Вызывать в транзакции.
     */
    default ItemAttachments getItemAttachments(Item item) {
        return ItemAttachments.NONE;
    }

    /**
     * Строки справочника, которые этому персонажу нужны в первую очередь: патроны и магазины к его оружию.
     */
    default List<CatalogItem> getSuggestedCatalog(Character character) {
        return List.of();
    }

    /**
     * Что сейчас важно знать о предмете сверх его полей; показывается в строке предмета.
     */
    default List<String> getItemNotes(Item item) {
        return List.of();
    }

    /**
     * Выполнить действие над предметом. Вызывать в транзакции.
     *
     * @param params параметры формы действия ({@code source}, {@code amount})
     * @return итог для показа игроку
     * @throws IllegalArgumentException если действие сейчас невозможно
     */
    default String performItemAction(Item item, String action, Map<String, String> params) {
        throw new IllegalArgumentException("Неизвестное действие с предметом: " + action);
    }

    /**
     * Сколько действий раунда даёт действие с предметом (энергетик Космозэков — ещё одно в этом ходу). Спрашивается до
     * {@link #performItemAction}: предмет после него может кончиться. Не в бою — ничего.
     */
    default int grantedActions(Item item, String action) {
        return 0;
    }

    /**
     * Предмет скопирован новому NPC: что не должно перейти (износ, расход), система убирает здесь.
     */
    default void prepareItemCopy(Item item) {
    }

    /**
     * Свойства предметов, в которых хранится id другого предмета (программа в деке): при импорте кампании их переводят на новые id.
     */
    default java.util.Set<String> getItemReferenceKeys() {
        return java.util.Set.of();
    }

    /**
     * Действие блока вживлённого: {@link InstalledBlock#actions()}. Вызывать в транзакции.
     *
     * @return итог для показа игроку
     * @throws IllegalArgumentException если действие сейчас невозможно
     */
    default String performInstalledAction(Character character, String action, Map<String, String> params) {
        throw new IllegalArgumentException("Такого действия нет: " + action);
    }

    /**
     * Что вживлено в персонажа; null — у системы такого нет.
     */
    default InstalledBlock getInstalled(Character character) {
        return null;
    }
}
