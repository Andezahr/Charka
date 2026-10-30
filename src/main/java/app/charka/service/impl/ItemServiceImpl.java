package app.charka.service.impl;

import app.charka.exception.NotFoundException;
import app.charka.item.CatalogItem;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.repository.InventoryRepository;
import app.charka.repository.ItemRepository;
import app.charka.service.ItemService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final InventoryRepository inventoryRepository;
    private final GameSystemRegistry systemRegistry;

    private static final String ITEM_NOT_FOUND = "Item not found for id=%d";

    /**
     * Получить все предметы, принадлежащие указанному инвентарю.
     *
     * @param inventoryId идентификатор инвентаря
     * @return список предметов; пустой список, если инвентарь пуст или не найден
     */
    @Override
    public List<Item> getByInventory(Long inventoryId) {
        // Если инвентарь не существует, вернется пустой список
        return itemRepository.findByInventoryId(inventoryId);
    }

    @Override
    public Item findById(Long id) {
        return itemRepository.findById(id).orElseThrow(() -> new NotFoundException(
                String.format(ITEM_NOT_FOUND, id)));
    }

    /**
     * Создать новый предмет и привязать его к указанному инвентарю.
     *
     * @param inventoryId идентификатор инвентаря, к которому будут добавлены предметы
     * @param item        объект предмета с заполненными полями name, quantity, cost, description
     * @return сохраненный объект Item с присвоенным id и ссылкой на Inventory
     * @throws NotFoundException если inventoryId некорректен (инвентарь не найден)
     */
    @Override
    public Item create(Long inventoryId, Item item) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new NotFoundException(
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
     * @throws NotFoundException если предмет с указанным id не найден
     */
    @Override
    public Item update(Long id, Item updatedItem) {
        Item existing = itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format(ITEM_NOT_FOUND, id)));
        existing.setName(updatedItem.getName());
        existing.setQuantity(updatedItem.getQuantity());
        existing.setCost(updatedItem.getCost());
        existing.setDescription(updatedItem.getDescription());
        return itemRepository.save(existing);
    }

    @Override
    public Item update(Long id, String name, Integer quantity, Integer cost, String description) {
        Item existing = itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format(ITEM_NOT_FOUND, id)));
        existing.setName(name);
        existing.setQuantity(quantity);
        existing.setCost(cost);
        existing.setDescription(description);
        return itemRepository.save(existing);
    }

    /**
     * Создать предмет заданного типа.
     *
     * @param typeCode   код типа из системы кампании
     * @param properties значения полей типа из формы
     * @throws IllegalArgumentException если тип недоступен в системе или значение не подходит полю
     */
    @Override
    public Item create(Long inventoryId, Item item, String typeCode, Map<String, String> properties) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new NotFoundException(
                        "Inventory not found for id=" + inventoryId));
        applyType(item, inventory, typeCode, properties);
        return create(inventoryId, item);
    }

    /**
     * Обновить предмет вместе с типом; значения прежнего типа не сохраняются.
     */
    @Override
    public Item update(Long id, String name, Integer quantity, Integer cost, String description,
                       String typeCode, Map<String, String> properties) {
        Item existing = update(id, name, quantity, cost, description);
        applyType(existing, existing.getInventory(), typeCode, properties);
        return existing;
    }

    /**
     * Добавить в инвентарь предмет из справочника системы кампании.
     *
     * @param key      код строки справочника
     * @param quantity количество, не меньше 1
     * @throws IllegalArgumentException если такого предмета в справочнике нет или количество неверное
     */
    @Override
    public Item addFromCatalog(Long inventoryId, String key, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Количество должно быть не меньше 1");
        }
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new NotFoundException(
                        "Inventory not found for id=" + inventoryId));
        CatalogItem entry = systemRegistry.forCharacter(inventory.getCharacter()).getItemCatalog().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Такого предмета нет в справочнике: " + key));
        int rows = entry.individual() ? quantity : 1;
        int perRow = entry.individual() ? 1 : quantity * entry.packSize();
        Item item = null;
        for (int row = 0; row < rows; row++) {
            item = new Item();
            item.setName(entry.name());
            item.setQuantity(perRow);
            item.setCost(entry.cost());
            item.setDescription(entry.description());
            item = create(inventoryId, item, entry.typeCode(), entry.properties());
        }
        return item;
    }

    /**
     * Выполнить действие над предметом по правилам системы кампании.
     *
     * @param characterId владелец из адреса; предмет другого персонажа считается ненайденным
     * @return итог для показа игроку
     * @throws IllegalArgumentException если действие сейчас невозможно
     */
    @Override
    public String performAction(Long characterId, Long itemId, String action, Map<String, String> params) {
        Item item = findById(itemId);
        if (!characterId.equals(item.getInventory().getCharacter().getId())) {
            throw new NotFoundException(String.format(ITEM_NOT_FOUND, itemId));
        }
        return systemRegistry.forCharacter(item.getInventory().getCharacter()).performItemAction(item, action, params);
    }

    private void applyType(Item item, Inventory inventory, String typeCode, Map<String, String> properties) {
        GameSystem system = systemRegistry.forCharacter(inventory.getCharacter());
        ItemTypeDefinition type = system.findItemType(typeCode)
                .orElseThrow(() -> new IllegalArgumentException("Тип предмета недоступен в этой системе: " + typeCode));
        Map<String, String> values = new HashMap<>(type.normalize(properties));
        if (type.code().equals(item.getType())) {
            type.stateKeys().forEach(key -> {
                if (item.getProperties().containsKey(key)) {
                    values.put(key, item.getProperties().get(key));
                }
            });
        }
        item.setType(type.code());
        item.getProperties().clear();
        item.getProperties().putAll(values);
    }

    @Override
    public void delete(Long id) {
        Item existing = itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format(ITEM_NOT_FOUND, id)));
        itemRepository.delete(existing);
    }

    /**
     * Переместить предмет в другой инвентарь.
     *
     * @param itemId       идентификатор предмета
     * @param targetInvId  идентификатор целевого инвентаря
     * @return обновленный и сохраненный объект Item с новым Inventory
     * @throws NotFoundException если предмет или целевой инвентарь не найдены
     */
    @Override
    public Item moveToInventory(Long itemId, Long targetInvId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException(
                        String.format(ITEM_NOT_FOUND, itemId)));
        Inventory target = inventoryRepository.findById(targetInvId)
                .orElseThrow(() -> new NotFoundException(
                        "Target inventory not found for id=" + targetInvId));
        // Перепривязываем к новому инвентарю
        item.setInventory(target);
        return itemRepository.save(item);
    }
}
