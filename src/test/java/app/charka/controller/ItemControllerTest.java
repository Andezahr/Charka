package app.charka.controller;


import app.charka.Routes;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.repository.InventoryRepository;
import app.charka.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemControllerTest {

    private InventoryRepository inventoryRepository;
    private ItemRepository itemRepository;
    private ItemController itemController;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        itemRepository = mock(ItemRepository.class);
        itemController = new ItemController();
    }

    @Test
    void addItem_success() {
        Inventory inventory = new Inventory();
        inventory.setId(2L);
        when(inventoryRepository.findById(2L)).thenReturn(Optional.of(inventory));

        String result = itemController.addItem(1L, 2L, "Меч");

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);

        verify(itemRepository).save(argThat(item ->
                "Меч".equals(item.getName()) &&
                        item.getInventory() == inventory
        ));
    }

    @Test
    void addItem_inventoryNotFound() {
        when(inventoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> itemController.addItem(1L, 99L, "Щит"));
    }

    @Test
    void editItem_success() {
        Item item = new Item();
        item.setId(5L);
        item.setName("Старое имя");
        when(itemRepository.findById(5L)).thenReturn(Optional.of(item));

        String result = itemController.editItem(1L, 2L, 5L, "Новое имя");

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);
        assertEquals("Новое имя", item.getName());
        verify(itemRepository).save(item);
    }

    @Test
    void editItem_notFound() {
        when(itemRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> itemController.editItem(1L, 2L, 77L, "Что-то"));
    }

    @Test
    void deleteItem_success() {
        String result = itemController.deleteItem(1L, 2L, 10L);

        assertEquals(Routes.CHARACTER_REDIRECT+1, result);
        verify(itemRepository).deleteById(10L);
    }
}