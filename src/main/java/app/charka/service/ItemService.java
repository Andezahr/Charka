package app.charka.service;

import app.charka.model.Item;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

public interface ItemService {

    @Transactional(readOnly = true)
    List<Item> getByInventory(Long inventoryId);

    @Transactional(readOnly = true)
    Item findById(Long id);

    @Transactional
    Item create(Long inventoryId, Item item);

    @Transactional
    Item update(Long id, Item updatedItem);

    @Transactional
    Item update(Long id, String name, Integer quantity, Integer cost, String description);

    @Transactional
    Item create(Long inventoryId, Item item, String typeCode, Map<String, String> properties);

    @Transactional
    Item update(Long id, String name, Integer quantity, Integer cost, String description,
                       String typeCode, Map<String, String> properties);

    @Transactional
    Item addFromCatalog(Long inventoryId, String key, int quantity);

    @Transactional
    String performAction(Long characterId, Long itemId, String action, Map<String, String> params);

    @Transactional
    void delete(Long id);

    @Transactional
    void changeQuantity(Long characterId, Long itemId, int delta);

    @Transactional
    Item moveToInventory(Long itemId, Long targetInvId);
}
