package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Item;
import app.charka.service.ItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemControllerTest {

    private ItemService itemService;
    private ItemController itemController;

    @BeforeEach
    void setUp() {
        itemService = mock(ItemService.class);
        itemController = new ItemController(itemService);
    }

    @Test
    void addItem_success() {
        String result = itemController.addItem(1L, 2L, "Меч", null, null, null);
        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemService).create(eq(2L), captor.capture());
        Item saved = captor.getValue();
        assertEquals("Меч", saved.getName());
        // quantity, cost, description are optional and null
        assertNull(saved.getQuantity());
        assertNull(saved.getCost());
        assertNull(saved.getDescription());
    }

    @Test
    void addItem_inventoryNotFound() {
        doThrow(new IllegalArgumentException("Inventory not found"))
                .when(itemService).create(eq(99L), any(Item.class));

        assertThrows(IllegalArgumentException.class,
                () -> itemController.addItem(1L, 99L, "Щит", null, null, null));
    }

    @Test
    void editItem_success() {
        String result = itemController.editItem(1L, 2L, 5L, "Новое имя", 3, 50, "Описание");
        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemService).update(eq(5L), captor.capture());
        Item updated = captor.getValue();
        assertEquals("Новое имя", updated.getName());
        assertEquals(Integer.valueOf(3), updated.getQuantity());
        assertEquals(Integer.valueOf(50), updated.getCost());
        assertEquals("Описание", updated.getDescription());
    }

    @Test
    void editItem_notFound() {
        doThrow(new IllegalArgumentException("Item not found"))
                .when(itemService).update(eq(77L), any(Item.class));

        assertThrows(IllegalArgumentException.class,
                () -> itemController.editItem(1L, 2L, 77L, "Что-то", null, null, null));
    }

    @Test
    void deleteItem_success() {
        String result = itemController.deleteItem(1L, 2L, 10L);
        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);
        verify(itemService).delete(10L);
    }

    @Test
    void moveItem_success() {
        String result = itemController.moveItem(1L, 2L, 5L, 7L);
        assertEquals(Routes.CHARACTER_REDIRECT + 1, result);
        verify(itemService).moveToInventory(5L, 7L);
    }

    @Test
    void moveItem_notFound() {
        doThrow(new IllegalArgumentException("Item not found"))
                .when(itemService).moveToInventory(999L, 7L);

        assertThrows(IllegalArgumentException.class,
                () -> itemController.moveItem(1L, 2L, 999L, 7L));
    }
}
