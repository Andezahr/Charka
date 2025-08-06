package app.charka.controller;

import app.charka.model.Inventory;
import app.charka.service.InventoryService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/character/{characterId}/inventories")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public String addInventory(
            @PathVariable Long characterId,
            @RequestParam String name
    ) {
        Inventory inventory = new Inventory();
        inventory.setName(name);
        inventoryService.create(
                characterId,
                inventory);
        return "redirect:/character/" + characterId;
    }

    @PostMapping("/{inventoryId}/edit")
    public String editInventory(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @RequestParam String name
    ) {
        Inventory updated = new Inventory();
        updated.setName(name);
        inventoryService.update(inventoryId, updated);
        return "redirect:/character/" + characterId;
    }

    @PostMapping("/{inventoryId}/delete")
    public String deleteInventory(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId
    ) {
        inventoryService.delete(inventoryId);
        return "redirect:/character/" + characterId;
    }
}
