package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Inventory;
import app.charka.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class InventoryControllerTest {

    private InventoryService inventoryService;
    private InventoryController inventoryController;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        inventoryController = new InventoryController(inventoryService);
    }

    @Test
    void addInventory_success() {
        String result = inventoryController.addInventory(1L, "Сумка");
        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryService).create(eq(1L), captor.capture());
        Inventory created = captor.getValue();
        assertEquals("Сумка", created.getName());
    }

    @Test
    void addInventory_characterNotFound() {
        doThrow(new IllegalArgumentException("Character not found"))
                .when(inventoryService).create(eq(99L), any(Inventory.class));

        assertThrows(IllegalArgumentException.class,
                () -> inventoryController.addInventory(99L, "Рюкзак"));
    }

    @Test
    void editInventory_success() {
        String result = inventoryController.editInventory(1L, 5L, "Новое имя");
        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryService).update(eq(5L), captor.capture());
        Inventory updated = captor.getValue();
        assertEquals("Новое имя", updated.getName());
    }

    @Test
    void editInventory_notFound() {
        doThrow(new IllegalArgumentException("Inventory not found"))
                .when(inventoryService).update(eq(77L), any(Inventory.class));

        assertThrows(IllegalArgumentException.class,
                () -> inventoryController.editInventory(1L, 77L, "Что-то"));
    }

    @Test
    void deleteInventory_success() {
        String result = inventoryController.deleteInventory(1L, 10L);
        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);
        verify(inventoryService).delete(10L);
    }
}
