package app.charka.service;

import app.charka.model.Item;
import app.charka.model.Inventory;
import app.charka.repository.ItemRepository;
import app.charka.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemService")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ItemServiceTest {

    @Mock private ItemRepository itemRepository;
    @Mock private InventoryRepository inventoryRepository;
    @InjectMocks private ItemService itemService;

    private static final String NOT_FOUND_MSG = "Item not found for id=%d";

    @Nested
    @DisplayName("getByInventory")
    @org.junit.jupiter.api.Order(1)
    class GetByInventoryTests {

        @Test
        @DisplayName("should return list when inventory has items")
        void shouldReturnList_WhenInventoryHasItems() {
            Long invId = 1L;
            List<Item> items = List.of(new Item(), new Item());
            when(itemRepository.findByInventoryId(invId)).thenReturn(items);

            List<Item> result = itemService.getByInventory(invId);

            assertThat(result).isSameAs(items);
            verify(itemRepository).findByInventoryId(invId);
        }

        @Test
        @DisplayName("should return empty list when none found")
        void shouldReturnEmptyList_WhenNoItems() {
            Long invId = 2L;
            when(itemRepository.findByInventoryId(invId)).thenReturn(List.of());

            List<Item> result = itemService.getByInventory(invId);

            assertThat(result).isEmpty();
            verify(itemRepository).findByInventoryId(invId);
        }
    }

    @Nested
    @DisplayName("findById")
    @org.junit.jupiter.api.Order(2)
    class FindByIdTests {

        @Test
        @DisplayName("should return Optional when exists")
        void shouldReturnOptional_WhenExists() {
            Item item = new Item();
            when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

            Item result = itemService.findById(1L);

            assertThat(result).isEqualTo(item);
            verify(itemRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw when item not exists")
        void shouldThrow_WhenNotExists() {
            Long id = 99L;
            when(itemRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> itemService.findById(id))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Item not found for id=" + id);

            verify(itemRepository).findById(id);
        }

    }

    @Nested
    @DisplayName("create")
    @org.junit.jupiter.api.Order(3)
    class CreateTests {

        @Test
        @DisplayName("should create and link to inventory when exists")
        void shouldCreateAndLink_WhenInventoryExists() {
            Long invId = 1L;
            Inventory inv = new Inventory();
            inv.setId(invId);
            Item toSave = new Item();
            toSave.setName("Sword");

            when(inventoryRepository.findById(invId)).thenReturn(Optional.of(inv));
            when(itemRepository.save(any(Item.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Item result = itemService.create(invId, toSave);

            assertThat(result.getInventory()).isSameAs(inv);
            verify(inventoryRepository).findById(invId);
            verify(itemRepository).save(toSave);
        }

        @Test
        @DisplayName("should throw when inventory not found")
        void shouldThrow_WhenInventoryNotFound() {
            Long invId = 99L;
            Item toSave = new Item();
            when(inventoryRepository.findById(invId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> itemService.create(invId, toSave))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Inventory not found for id=" + invId);

            verify(itemRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("update by Item")
    @org.junit.jupiter.api.Order(4)
    class UpdateObjectTests {

        @Test
        @DisplayName("should update fields when exists")
        void shouldUpdateFields_WhenExists() {
            Long id = 1L;
            Item existing = new Item();
            existing.setId(id);
            existing.setName("Old");
            existing.setQuantity(1);
            existing.setCost(10);
            existing.setDescription("old");

            Item updated = new Item();
            updated.setName("New");
            updated.setQuantity(5);
            updated.setCost(50);
            updated.setDescription("new");

            when(itemRepository.findById(id)).thenReturn(Optional.of(existing));
            when(itemRepository.save(existing)).thenReturn(existing);

            Item result = itemService.update(id, updated);

            assertThat(result.getName()).isEqualTo("New");
            assertThat(result.getQuantity()).isEqualTo(5);
            assertThat(result.getCost()).isEqualTo(50);
            assertThat(result.getDescription()).isEqualTo("new");
            verify(itemRepository).save(existing);
        }

        @Test
        @DisplayName("should throw when item not found")
        void shouldThrow_WhenNotFound() {
            Long id = 99L;
            when(itemRepository.findById(id)).thenReturn(Optional.empty());

            Item nonExistent = new Item();

            assertThatThrownBy(() -> itemService.update(id, nonExistent))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(String.format(NOT_FOUND_MSG, id));
        }
    }

    @Nested
    @DisplayName("update by fields")
    @org.junit.jupiter.api.Order(5)
    class UpdateFieldsTests {

        @ParameterizedTest
        @DisplayName("should update via parameters")
        @ValueSource(longs = {1L, 2L})
        void shouldUpdateViaParams(Long id) {
            Item existing = new Item();
            existing.setId(id);
            when(itemRepository.findById(id)).thenReturn(Optional.of(existing));
            when(itemRepository.save(existing)).thenReturn(existing);

            Item result = itemService.update(id, "N", 2, 20, "D");

            assertThat(result.getName()).isEqualTo("N");
            assertThat(result.getQuantity()).isEqualTo(2);
            assertThat(result.getCost()).isEqualTo(20);
            assertThat(result.getDescription()).isEqualTo("D");
            verify(itemRepository).save(existing);
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrow_WhenNotFound() {
            Long id = 100L;
            when(itemRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> itemService.update(id, "N", 1,1,"D"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(String.format(NOT_FOUND_MSG, id));
        }
    }

    @Nested
    @DisplayName("delete")
    @org.junit.jupiter.api.Order(6)
    class DeleteTests {

        @Test
        @DisplayName("should delete when exists")
        void shouldDelete_WhenExists() {
            Long id = 1L;
            Item existing = new Item();
            when(itemRepository.findById(id)).thenReturn(Optional.of(existing));

            itemService.delete(id);

            verify(itemRepository).delete(existing);
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrow_WhenNotFound() {
            Long id = 50L;
            when(itemRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> itemService.delete(id))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(String.format(NOT_FOUND_MSG, id));

            verify(itemRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("moveToInventory")
    @org.junit.jupiter.api.Order(7)
    class MoveTests {

        @Test
        @DisplayName("should move when both exist")
        void shouldMove_WhenBothExist() {
            Long itemId = 1L, targetId = 2L;
            Item item = new Item();
            Inventory target = new Inventory();
            when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(inventoryRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(itemRepository.save(item)).thenReturn(item);

            Item result = itemService.moveToInventory(itemId, targetId);

            assertThat(result.getInventory()).isSameAs(target);
            verify(itemRepository).save(item);
        }

        @Test
        @DisplayName("should throw when item missing")
        void shouldThrow_WhenItemMissing() {
            when(itemRepository.findById(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> itemService.moveToInventory(5L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(String.format(NOT_FOUND_MSG, 5L));
        }

        @Test
        @DisplayName("should throw when target missing")
        void shouldThrow_WhenTargetMissing() {
            Item item = new Item();
            when(itemRepository.findById(3L)).thenReturn(Optional.of(item));
            when(inventoryRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> itemService.moveToInventory(3L, 9L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Target inventory not found for id=9");
        }
    }
}
