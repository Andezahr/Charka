package app.charka.service;

import app.charka.model.Item;
import app.charka.model.Inventory;
import app.charka.repository.ItemRepository;
import app.charka.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final InventoryRepository inventoryRepository;

    private static final String ITEM_NOT_FOUND = "Item not found for id=%d";

    /**
     * Получить все предметы, принадлежащие указанному инвентарю.
     *
     * @param inventoryId идентификатор инвентаря
     * @return список предметов; пустой список, если инвентарь пуст или не найден
     */
    @Transactional(readOnly = true)
    public List<Item> getByInventory(Long inventoryId) {
        // Если инвентарь не существует, вернется пустой список
        return itemRepository.findByInventoryId(inventoryId);
    }


    @Transactional(readOnly = true)
    public Item findById(Long id) {
        return itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException(
                String.format(ITEM_NOT_FOUND, id)));
    }

    /**
     * Создать новый предмет и привязать его к указанному инвентарю.
     *
     * @param inventoryId идентификатор инвентаря, к которому будут добавлены предметы
     * @param item        объект предмета с заполненными полями name, quantity, cost, description
     * @return сохраненный объект Item с присвоенным id и ссылкой на Inventory
     * @throws IllegalArgumentException если inventoryId некорректен (инвентарь не найден)
     */
    @Transactional
    public Item create(Long inventoryId, Item item) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Inventory not found for id=" + inventoryId));
        item.setInventory(inventory);
        return itemRepository.save(item);
    }

    /**
     * Обновить данные существующего предмета.
     *
     * @param id          идентификатор предмета для обновления
     * @param updatedItem объект с новыми значениями полей name, quantity, cost, description
     * @return обновленный и сохраненный объект Item
     * @throws IllegalArgumentException если предмет с указанным id не найден
     */
    @Transactional
    public Item update(Long id, Item updatedItem) {
        Item existing = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(ITEM_NOT_FOUND, id)));
        existing.setName(updatedItem.getName());
        existing.setQuantity(updatedItem.getQuantity());
        existing.setCost(updatedItem.getCost());
        existing.setDescription(updatedItem.getDescription());
        return itemRepository.save(existing);
    }

    @Transactional
    public Item update(Long id, String name, Integer quantity, Integer cost, String description) {
        Item existing = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(ITEM_NOT_FOUND, id)));
        existing.setName(name);
        existing.setQuantity(quantity);
        existing.setCost(cost);
        existing.setDescription(description);
        return itemRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Item existing = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(ITEM_NOT_FOUND, id)));
        itemRepository.delete(existing);
    }

    /**
     * Переместить предмет в другой инвентарь.
     *
     * @param itemId       идентификатор предмета
     * @param targetInvId  идентификатор целевого инвентаря
     * @return обновленный и сохраненный объект Item с новым Inventory
     * @throws IllegalArgumentException если предмет или целевой инвентарь не найдены
     */
    @Transactional
    public Item moveToInventory(Long itemId, Long targetInvId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(ITEM_NOT_FOUND, itemId)));
        Inventory target = inventoryRepository.findById(targetInvId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Target inventory not found for id=" + targetInvId));
        // Перепривязываем к новому инвентарю
        item.setInventory(target);
        return itemRepository.save(item);
    }
}
