package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.repository.CharacterRepository;
import app.charka.repository.InventoryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class InventoryController {

    private final CharacterRepository characterRepository;
    private final InventoryRepository inventoryRepository;

    public InventoryController(CharacterRepository characterRepository, InventoryRepository inventoryRepository) {
        this.characterRepository = characterRepository;
        this.inventoryRepository = inventoryRepository;
    }

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
