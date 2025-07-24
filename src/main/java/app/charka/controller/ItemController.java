package app.charka.controller;

import app.charka.model.Item;
import app.charka.service.ItemService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/character/{characterId}/inventories/{inventoryId}/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
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
        return "redirect:/character/" + characterId;
    }

    @PostMapping("/{itemId}/edit")
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
        return "redirect:/character/" + characterId;
    }

    @PostMapping("/{itemId}/delete")
    public String deleteItem(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @PathVariable Long itemId
    ) {
        itemService.delete(itemId);
        return "redirect:/character/" + characterId;
    }

    @PostMapping("/{itemId}/move")
    public String moveItem(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @PathVariable Long itemId,
            @RequestParam Long targetInventoryId
    ) {
        itemService.moveToInventory(itemId, targetInventoryId);
        return "redirect:/character/" + characterId;
    }
}
