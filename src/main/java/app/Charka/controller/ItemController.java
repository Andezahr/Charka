package app.Charka.controller;

import app.Charka.Routes;
import app.Charka.model.Inventory;
import app.Charka.model.Item;
import app.Charka.repository.InventoryRepository;
import app.Charka.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ItemController {
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private ItemRepository itemRepository;


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
