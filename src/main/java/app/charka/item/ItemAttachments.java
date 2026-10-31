package app.charka.item;

import app.charka.model.Inventory;
import app.charka.model.Item;

import java.util.List;

/**
 * Что показывается вместе с предметом, а не своими строками: магазины и патроны оружия. Список даёт {@code GameSystem.getItemAttachments(Item)}.
 *
 * @param groups     фишки по группам
 * @param items      сами предметы; лежащие в том же инвентаре свёрнуты под предметом и не занимают свои строки
 * @param itemsTitle подпись свёрнутого списка
 * @param warning    что мешает пользоваться предметом; null — ничего
 */
public record ItemAttachments(List<Group> groups, List<Item> items, String itemsTitle, String warning) {

    public static final ItemAttachments NONE = new ItemAttachments(List.of(), List.of(), "", null);

    /**
     * @param title подпись: «магазины»
     * @param chips фишки в порядке показа
     */
    public record Group(String title, List<ItemChip> chips) {
        public Group {
            chips = List.copyOf(chips);
        }
    }

    public ItemAttachments {
        groups = List.copyOf(groups);
        items = List.copyOf(items);
    }

    /** Предметы из этого инвентаря; остальные показаны в своём */
    public List<Item> itemsIn(Inventory inventory) {
        return items.stream().filter(item -> item.getInventory() == inventory).toList();
    }
}
