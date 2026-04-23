package app.charka.service;

import app.charka.model.Item;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ItemService {
    List<Item> getByInventory(Long inventoryId);
    Item findById(Long id);
    Item create(Long inventoryId, Item item);
    Item update(Long id, Item updatedItem);
    Item update(Long id, String name, Integer quantity, Integer cost, String description);
    void delete(Long id);
    Item moveToInventory(Long itemId, Long targetInvId);
}
