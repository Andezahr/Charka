package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Item;
import app.charka.service.ItemService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping(Routes.ITEM_ADD)
    public String addItem(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @RequestParam String itemName,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false) Integer cost,
            @RequestParam(required = false) String description
    ) {
        Item item = new Item();
        item.setName(itemName);
        item.setQuantity(quantity);
        item.setCost(cost);
        item.setDescription(description);
        itemService.create(inventoryId, item);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.ITEM_EDIT)
    public String editItem(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @PathVariable Long itemId,
            @RequestParam String itemName,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false) Integer cost,
            @RequestParam(required = false) String description
    ) {
        itemService.update(itemId, itemName, quantity, cost, description);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.ITEM_DELETE)
    public String deleteItem(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @PathVariable Long itemId
    ) {
        itemService.delete(itemId);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.ITEM_MOVE)
    public String moveItem(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @PathVariable Long itemId,
            @RequestParam Long targetInventoryId
    ) {
        itemService.moveToInventory(itemId, targetInventoryId);
        return Routes.CHARACTER_REDIRECT + characterId;
    }
}
