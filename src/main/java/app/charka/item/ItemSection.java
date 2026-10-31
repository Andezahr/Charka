package app.charka.item;

import app.charka.model.Inventory;
import app.charka.model.Item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Секция инвентаря на листе: оружие отдельно от вещей.
 *
 * @param title заголовок
 * @param items предметы секции, без тех, что показаны под другим предметом
 */
public record ItemSection(String title, List<Item> items) {

    public ItemSection {
        items = List.copyOf(items);
    }

    /**
     * Разбивает инвентарь на секции в порядке типов системы; пустых секций нет. Предмет неизвестного системе типа попадает в секцию обычных предметов.
     *
     * @param attachments что показывается вместе с каждым предметом, по id предмета
     */
    public static List<ItemSection> of(Inventory inventory, List<ItemTypeDefinition> types, Map<Long, ItemAttachments> attachments) {
        Set<Long> shownUnderOther = new HashSet<>();
        inventory.getContent().forEach(item -> attachments.getOrDefault(item.getId(), ItemAttachments.NONE).itemsIn(inventory)
                .forEach(attached -> shownUnderOther.add(attached.getId())));

        Map<String, List<Item>> bySection = new LinkedHashMap<>();
        types.forEach(type -> bySection.putIfAbsent(type.section(), new ArrayList<>()));
        for (Item item : inventory.getContent()) {
            if (shownUnderOther.contains(item.getId())) {
                continue;
            }
            String section = types.stream()
                    .filter(type -> type.code().equals(item.getType()))
                    .map(ItemTypeDefinition::section)
                    .findFirst()
                    .orElse(ItemTypeDefinition.GENERIC.section());
            bySection.computeIfAbsent(section, title -> new ArrayList<>()).add(item);
        }
        return bySection.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> new ItemSection(entry.getKey(), entry.getValue()))
                .toList();
    }
}
