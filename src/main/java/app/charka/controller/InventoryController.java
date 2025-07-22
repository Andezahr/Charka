package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Inventory;
import app.charka.service.InventoryService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping(Routes.INVENTORY_ADD)
    public String addInventory(
            @PathVariable Long characterId,
            @RequestParam String name
    ) {
        Inventory inventory = new Inventory();
        inventory.setName(name);
        inventoryService.create(characterId, inventory);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.INVENTORY_EDIT)
    public String editInventory(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId,
            @RequestParam String name
    ) {
        Inventory updated = new Inventory();
        updated.setName(name);
        inventoryService.update(inventoryId, updated);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.INVENTORY_DELETE)
    public String deleteInventory(
            @PathVariable Long characterId,
            @PathVariable Long inventoryId
    ) {
        inventoryService.delete(inventoryId);
        return Routes.CHARACTER_REDIRECT + characterId;
    }
}
