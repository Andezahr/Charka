package app.charka.controller;

import app.charka.GlobalExceptionHandler;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.InventoryNotFoundException;
import app.charka.model.Inventory;
import app.charka.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryController – unit-тесты через Standalone MockMvc")
class InventoryControllerTest {

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryController inventoryController;

    private MockMvc mockMvc;

    private static final long CHAR_ID        = 1L;
    private static final long MISSING_CHAR_ID = 99L;
    private static final long INV_ID         = 5L;
    private static final long DELETE_INV_ID  = 10L;
    private static final String NAME_SAMPLE  = "Сумка";
    private static final String REDIRECT_URL = "/character/" + CHAR_ID;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(inventoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("Добавление инвентаря")
    class AddInventoryTests {

        @Test
        @DisplayName("302 – успешное добавление инвентаря")
        void addInventory_success() throws Exception {
            // Given
            Inventory createdInventory = new Inventory();
            createdInventory.setName(NAME_SAMPLE);
            when(inventoryService.create(eq(CHAR_ID), any(Inventory.class)))
                    .thenReturn(createdInventory);

            // When + Then
            mockMvc.perform(post("/character/{characterId}/inventories", CHAR_ID)
                            .param("name", NAME_SAMPLE)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            // Verify
            ArgumentCaptor<Inventory> inventoryCaptor = ArgumentCaptor.forClass(Inventory.class);
            verify(inventoryService).create(eq(CHAR_ID), inventoryCaptor.capture());

            Inventory capturedInventory = inventoryCaptor.getValue();
            assertEquals(NAME_SAMPLE, capturedInventory.getName());

            verifyNoMoreInteractions(inventoryService);
        }

        @Test
        @DisplayName("500 – персонаж не найден")
        void addInventory_characterNotFound() throws Exception {
            // Given - исключение бросается из inventoryService, не characterService
            when(inventoryService.create(eq(MISSING_CHAR_ID), any(Inventory.class)))
                    .thenThrow(new CharacterNotFoundException("Character not found"));

            // When + Then
            mockMvc.perform(post("/character/{characterId}/inventories", MISSING_CHAR_ID)
                            .param("name", "Рюкзак")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(containsString("Character not found")));

            // Verify
            verify(inventoryService).create(eq(MISSING_CHAR_ID), any(Inventory.class));
            verifyNoMoreInteractions(inventoryService);
        }
    }

    @Nested
    @DisplayName("Редактирование инвентаря")
    class EditInventoryTests {

        @Test
        @DisplayName("302 – успешное редактирование инвентаря")
        void editInventory_success() throws Exception {
            // Given
            Inventory updatedInventory = new Inventory();
            updatedInventory.setName("Новое имя");
            when(inventoryService.update(eq(INV_ID), any(Inventory.class)))
                    .thenReturn(updatedInventory);

            // When + Then
            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/edit", CHAR_ID, INV_ID)
                            .param("name", "Новое имя")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            // Verify
            ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
            verify(inventoryService).update(eq(INV_ID), captor.capture());
            Inventory captured = captor.getValue();

            assertEquals("Новое имя", captured.getName());
            verifyNoMoreInteractions(inventoryService);
        }

        @Test
        @DisplayName("500 – инвентарь не найден")
        void editInventory_notFound() throws Exception {
            // Given
            doThrow(new InventoryNotFoundException("Inventory with id " + INV_ID + " not found"))
                    .when(inventoryService).update(eq(INV_ID), any(Inventory.class));

            // When + Then
            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/edit", CHAR_ID, INV_ID)
                            .param("name", "Что-то")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(containsString("Inventory with id " + INV_ID + " not found")));

            // Verify
            verify(inventoryService).update(eq(INV_ID), any(Inventory.class));
            verifyNoMoreInteractions(inventoryService);
        }
    }

    @Nested
    @DisplayName("Удаление инвентаря")
    class DeleteInventoryTests {

        @Test
        @DisplayName("302 – успешное удаление инвентаря")
        void deleteInventory_success() throws Exception {
            // When + Then
            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/delete", CHAR_ID, DELETE_INV_ID))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            // Verify
            verify(inventoryService).delete(DELETE_INV_ID);
            verifyNoMoreInteractions(inventoryService);
        }

        @Test
        @DisplayName("500 – ошибка при удалении")
        void deleteInventory_error() throws Exception {
            // Given
            doThrow(new InventoryNotFoundException("Inventory with id " + DELETE_INV_ID + " not found"))
                    .when(inventoryService).delete(DELETE_INV_ID);

            // When + Then
            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/delete", CHAR_ID, DELETE_INV_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(containsString("Inventory with id " + DELETE_INV_ID + " not found")));

            // Verify
            verify(inventoryService).delete(DELETE_INV_ID);
            verifyNoMoreInteractions(inventoryService);
        }
    }
}
