package app.Charka.controller;

import app.Charka.Routes;
import app.Charka.model.Character;
import app.Charka.model.Inventory;
import app.Charka.repository.CharacterRepository;
import app.Charka.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class InventoryController {
    @Autowired
    private CharacterRepository characterRepository;
    @Autowired
    private InventoryRepository inventoryRepository;

    @PostMapping(Routes.INVENTORY_ADD)
    public String addInventory(@PathVariable Long characterId, @RequestParam String name) {
        Character character = characterRepository.findById(characterId).orElseThrow();
        Inventory inventory = new Inventory();
        inventory.setName(name);
        inventory.setCharacter(character);
        inventoryRepository.save(inventory);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.INVENTORY_EDIT)
    public String editInventory(@PathVariable Long characterId, @PathVariable Long inventoryId, @RequestParam String name) {
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow();
        inventory.setName(name);
        inventoryRepository.save(inventory);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.INVENTORY_DELETE)
    public String deleteInventory(@PathVariable Long characterId, @PathVariable Long inventoryId) {
        inventoryRepository.deleteById(inventoryId);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

}
