package app.charka.service;

import app.charka.model.Inventory;
import app.charka.model.Item;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface InventoryService {

    @Transactional(readOnly = true)
    List<Inventory> getByCharacter(Long characterId);

    @Transactional(readOnly = true)
    Inventory getById(Long id);

    @Transactional
    Inventory create(Long characterId, Inventory inventory);

    @Transactional
    Inventory update(Long id, Inventory updatedInventory);

    @Transactional
    void delete(Long id);

    @Transactional(readOnly = true)
    List<Item> getContent(Long id);
}
