package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.repository.InventoryRepository;
import app.charka.repository.ItemRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ItemController {

    private final InventoryRepository inventoryRepository;
    private final ItemRepository itemRepository;

    public ItemController(InventoryRepository inventoryRepository, ItemRepository itemRepository) {
        this.inventoryRepository = inventoryRepository;
        this.itemRepository = itemRepository;
    }


    @PostMapping(Routes.ITEM_ADD)
    public String addItem(@PathVariable Long characterId, @PathVariable Long inventoryId, @RequestParam String itemName) {
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow();
        Item item = new Item();
        item.setName(itemName);
        item.setInventory(inventory);
        itemRepository.save(item);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.ITEM_EDIT)
    public String editItem(@PathVariable Long characterId, @PathVariable Long inventoryId, @PathVariable Long itemId, @RequestParam String itemName) {
        Item item = itemRepository.findById(itemId).orElseThrow();
        item.setName(itemName);
        itemRepository.save(item);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.ITEM_DELETE)
    public String deleteItem(@PathVariable Long characterId, @PathVariable Long inventoryId, @PathVariable Long itemId) {
        itemRepository.deleteById(itemId);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

}
