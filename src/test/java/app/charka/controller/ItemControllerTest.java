package app.charka.controller;

import app.charka.exception.NotFoundException;
import app.charka.exception.GlobalExceptionHandler;
import app.charka.model.Item;
import app.charka.service.ItemService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("ItemController – unit-тесты через Standalone MockMvc")
class ItemControllerTest {

    // ======== DEPENDENCIES ========
    @Mock private ItemService itemService;
    @InjectMocks private ItemController itemController;

    // ======== INFRASTRUCTURE ========
    private MockMvc mockMvc;

    // ======== CONSTANTS ========
    private static final long CHAR_ID        = 1L;
    private static final long INV_ID         = 2L;
    private static final long MISSING_INV_ID = 99L;
    private static final long ITEM_ID        = 5L;
    private static final long DELETE_ITEM_ID = 10L;
    private static final long MOVE_ITEM_ID   = 5L;
    private static final long TARGET_INV_ID  = 7L;

    private static final String NAME_SWORD       = "Меч";
    private static final String NAME_SHIELD      = "Щит";
    private static final String NAME_NEW         = "Новое имя";
    private static final String DESCRIPTION      = "Описание";
    private static final String ERR_INV_NOT_FOUND = "Inventory not found";
    private static final String ERR_ITEM_NOT_FOUND = "Item not found";
    private static final String ERR_DELETE       = "Delete error";
    private static final String REDIRECT_URL     = "/character/" + CHAR_ID;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(itemController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ======================================================================
    //                           ADD ITEM BLOCK
    // ======================================================================
    @Nested
    @DisplayName("Добавление предмета")
    class AddItemTests {

        @Test
        @DisplayName("302 – успешное добавление предмета")
        void addItem_success() throws Exception {
            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/items",
                            CHAR_ID, INV_ID)
                            .param("itemName", NAME_SWORD)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
            verify(itemService).create(eq(INV_ID), captor.capture(), eq("GENERIC"), eq(java.util.Map.of()));
            Item saved = captor.getValue();

            assertAll("Поля Item при добавлении",
                    () -> assertEquals(NAME_SWORD, saved.getName()),
                    () -> assertNull(saved.getQuantity()),
                    () -> assertNull(saved.getCost()),
                    () -> assertNull(saved.getDescription())
            );
            verifyNoMoreInteractions(itemService);
        }

        @Test
        @DisplayName("404 – инвентарь не найден")
        void addItem_inventoryNotFound() throws Exception {
            doThrow(new NotFoundException(ERR_INV_NOT_FOUND))
                    .when(itemService).create(eq(MISSING_INV_ID), any(), any(), any());

            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/items",
                            CHAR_ID, MISSING_INV_ID)
                            .param("itemName", NAME_SHIELD)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isNotFound())
                    .andExpect(model().attribute("message", ERR_INV_NOT_FOUND));

            verify(itemService).create(eq(MISSING_INV_ID), any(), any(), any());
            verifyNoMoreInteractions(itemService);
        }
    }

    // ======================================================================
    //                          EDIT ITEM BLOCK
    // ======================================================================
    @Nested
    @DisplayName("Редактирование предмета")
    class EditItemTests {

        @Test
        @DisplayName("302 – успешное редактирование предмета")
        void editItem_success() throws Exception {
            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/items/{itemId}/edit",
                            CHAR_ID, INV_ID, ITEM_ID)
                            .param("itemName", NAME_NEW)
                            .param("quantity", "3")
                            .param("cost", "50")
                            .param("description", DESCRIPTION)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            verify(itemService).update(
                    ITEM_ID,
                    NAME_NEW,
                    3,
                    50,
                    DESCRIPTION,
                    "GENERIC",
                    java.util.Map.of()
            );
            verifyNoMoreInteractions(itemService);
        }

        @Test
        @DisplayName("404 – предмет не найден")
        void editItem_notFound() throws Exception {
            doThrow(new NotFoundException(ERR_ITEM_NOT_FOUND))
                    .when(itemService).update(eq(ITEM_ID), any(), any(), any(), any(), any(), any());

            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/items/{itemId}/edit",
                            CHAR_ID, INV_ID, ITEM_ID)
                            .param("itemName", NAME_NEW)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isNotFound())
                    .andExpect(model().attribute("message", ERR_ITEM_NOT_FOUND));

            verify(itemService).update(eq(ITEM_ID), any(), any(), any(), any(), any(), any());
            verifyNoMoreInteractions(itemService);
        }
    }

    // ======================================================================
    //                         DELETE ITEM BLOCK
    // ======================================================================
    @Nested
    @DisplayName("Удаление предмета")
    class DeleteItemTests {

        @Test
        @DisplayName("302 – успешное удаление предмета")
        void deleteItem_success() throws Exception {
            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/items/{itemId}/delete",
                            CHAR_ID, INV_ID, DELETE_ITEM_ID))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            verify(itemService).delete(DELETE_ITEM_ID);
            verifyNoMoreInteractions(itemService);
        }

        @Test
        @DisplayName("500 – ошибка при удалении")
        void deleteItem_error() throws Exception {
            doThrow(new RuntimeException(ERR_DELETE))
                    .when(itemService).delete(DELETE_ITEM_ID);

            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/items/{itemId}/delete",
                            CHAR_ID, INV_ID, DELETE_ITEM_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(model().attribute("message", ERR_DELETE));

            verify(itemService).delete(DELETE_ITEM_ID);
            verifyNoMoreInteractions(itemService);
        }
    }

    // ======================================================================
    //                          MOVE ITEM BLOCK
    // ======================================================================
    @Nested
    @DisplayName("Перемещение предмета")
    class MoveItemTests {

        @Test
        @DisplayName("302 – успешное перемещение предмета")
        void moveItem_success() throws Exception {
            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/items/{itemId}/move",
                            CHAR_ID, INV_ID, MOVE_ITEM_ID)
                            .param("targetInventoryId", String.valueOf(TARGET_INV_ID))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            verify(itemService).moveToInventory(MOVE_ITEM_ID, TARGET_INV_ID);
            verifyNoMoreInteractions(itemService);
        }

        @Test
        @DisplayName("404 – предмет не найден для перемещения")
        void moveItem_notFound() throws Exception {
            doThrow(new NotFoundException(ERR_ITEM_NOT_FOUND))
                    .when(itemService).moveToInventory(MOVE_ITEM_ID, TARGET_INV_ID);

            mockMvc.perform(post("/character/{characterId}/inventories/{inventoryId}/items/{itemId}/move",
                            CHAR_ID, INV_ID, MOVE_ITEM_ID)
                            .param("targetInventoryId", String.valueOf(TARGET_INV_ID))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isNotFound())
                    .andExpect(model().attribute("message", ERR_ITEM_NOT_FOUND));

            verify(itemService).moveToInventory(MOVE_ITEM_ID, TARGET_INV_ID);
            verifyNoMoreInteractions(itemService);
        }
    }

    @Nested
    @DisplayName("Тип предмета")
    class ItemTypeTests {

        @Test
        @DisplayName("поля типа приходят параметрами prop.*, остальное в них не попадает")
        void properties() throws Exception {
            mockMvc.perform(post("/character/{c}/inventories/{i}/items", CHAR_ID, INV_ID)
                            .param("itemName", NAME_SWORD)
                            .param("itemType", "CP2020_WEAPON")
                            .param("prop.category", "RIF")
                            .param("prop.range", "400"))
                    .andExpect(status().is3xxRedirection());

            verify(itemService).create(eq(INV_ID), any(), eq("CP2020_WEAPON"),
                    eq(java.util.Map.of("category", "RIF", "range", "400")));
        }

        @Test
        @DisplayName("предмет из справочника: код и количество уходят в сервис")
        void fromCatalog() throws Exception {
            mockMvc.perform(post("/character/{c}/inventories/{i}/items/catalog", CHAR_ID, INV_ID)
                            .param("catalogKey", "arasaka_minami_10")
                            .param("quantity", "2"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL));

            verify(itemService).addFromCatalog(INV_ID, "arasaka_minami_10", 2);
        }

        @Test
        @DisplayName("некорректный ввод — возврат на лист с сообщением, а не страница ошибки")
        void badInput() throws Exception {
            doThrow(new IllegalArgumentException("Поле «Тип» обязательно"))
                    .when(itemService).update(eq(ITEM_ID), any(), any(), any(), any(), any(), any());

            mockMvc.perform(post("/character/{c}/inventories/{i}/items/{id}/edit", CHAR_ID, INV_ID, ITEM_ID)
                            .param("itemName", NAME_NEW)
                            .param("itemType", "CP2020_WEAPON"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(REDIRECT_URL))
                    .andExpect(flash().attribute("errorMessage", "Поле «Тип» обязательно"));
        }
    }
}
