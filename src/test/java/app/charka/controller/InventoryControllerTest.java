package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.repository.CharacterRepository;
import app.charka.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryControllerTest {

    private CharacterRepository characterRepository;
    private InventoryRepository inventoryRepository;
    private InventoryController inventoryController;

    @BeforeEach
    void setUp() {
        characterRepository = mock(CharacterRepository.class);
        inventoryRepository = mock(InventoryRepository.class);
        inventoryController = new InventoryController();
    }

    @Test
    void addInventory_success() {
        Character character = new Character();
        character.setId(1L);
        when(characterRepository.findById(1L)).thenReturn(Optional.of(character));

        String result = inventoryController.addInventory(1L, "Сумка");

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);

        verify(inventoryRepository).save(argThat(inv ->
                "Сумка".equals(inv.getName()) &&
                        inv.getCharacter() == character
        ));
    }

    @Test
    void addInventory_characterNotFound() {
        when(characterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> inventoryController.addInventory(99L, "Рюкзак"));
    }

    @Test
    void editInventory_success() {
        Inventory inventory = new Inventory();
        inventory.setId(5L);
        inventory.setName("Старое имя");
        when(inventoryRepository.findById(5L)).thenReturn(Optional.of(inventory));

        String result = inventoryController.editInventory(1L, 5L, "Новое имя");

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);
        assertEquals("Новое имя", inventory.getName());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void editInventory_notFound() {
        when(inventoryRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> inventoryController.editInventory(1L, 77L, "Что-то"));
    }

    @Test
    void deleteInventory_success() {
        String result = inventoryController.deleteInventory(1L, 10L);

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);
        verify(inventoryRepository).deleteById(10L);
    }
}