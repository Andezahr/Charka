package app.charka.service;

import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@RequiredArgsConstructor
public class InventoryServiceTest {
    @Mock private InventoryRepository inventoryRepository;
    @InjectMocks private InventoryService inventoryService;

    /**
     * Creating inventory for character
     */

    @Nested
    @DisplayName("Creating inventory for character")
    @Order(1)
    class CreateInventoryTests {

        private Character testCharacter;
        private Inventory testInventory;

        @BeforeEach
        void setUp() {
            testCharacter = createTestCharacter(1L, "Aragorn");
            testInventory = createTestInventory(null, "Backpack", null);
        }


    @Test
    @DisplayName("should create inventory successfully when character exists")
    void shouldCreateInventorySuccessfully_WhenCharacterExists() {
        Inventory savedInventory = createTestInventory(1L, "Backpack", testCharacter);

        when(inventoryRepository.save(any(Inventory.class))).thenReturn(savedInventory);

        Inventory result = inventoryService.create(testCharacter, testInventory);

        assertThat(result)
                .extracting(Inventory::getId, Inventory::getName, Inventory::getCharacter)
                .containsExactly(1L, "Backpack", testCharacter);

        verify(inventoryRepository).save(any(Inventory.class));
    }}




    private Character createTestCharacter(Long id, String name) {
        Character ch = new Character();
        ch.setId(id);
        ch.setName(name);
        return ch;
    }
    private Inventory createTestInventory(Long id, String name, Character character) {
        Inventory inv = new Inventory();
        inv.setId(id);
        inv.setName(name);
        inv.setCharacter(character);
        return inv;
    }
}
