package app.charka.service;

import app.charka.model.Inventory;
import app.charka.model.Item;

import java.util.List;

public interface InventoryService {
    List<Inventory> getByCharacter(Long characterId);
    Inventory getById(Long id);
    Inventory create(Long characterId, Inventory inventory);
    Inventory update(Long id, Inventory updatedInventory);
    void delete(Long id);
    List<Item> getContent(Long id);
}
