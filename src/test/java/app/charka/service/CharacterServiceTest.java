package app.charka.service;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.repository.CharacterRepository;
import app.charka.service.campaign.CampaignService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CharacterService")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CharacterServiceTest {

    @Mock private CharacterRepository characterRepository;
    @Mock private CampaignService   campaignService;
    @Mock private InventoryService  inventoryService;
    @InjectMocks private CharacterService characterService;

    /* ──────────────────── Creating character in campaign ─────────────────── */

    @Nested
    @DisplayName("Creating character in campaign")
    @Order(1)
    class CreateInCampaignTests {

        private Campaign  testCampaign;
        private Character testCharacter;

        @BeforeEach
        void setUp() {
            testCampaign  = createTestCampaign(1L, "Test Campaign");
            testCharacter = createTestCharacter(null, "Aragorn", null);
        }

        @Test
        @DisplayName("should create character successfully when campaign exists")
        void shouldCreateCharacterSuccessfully_WhenCampaignExists() {
            Long campaignId = 1L;
            Character savedCharacter = createTestCharacter(1L, "Aragorn", testCampaign);

            when(campaignService.getById(campaignId)).thenReturn(testCampaign);
            when(characterRepository.save(any(Character.class))).thenReturn(savedCharacter);

            Character result = characterService.createInCampaign(campaignId, testCharacter);

            assertThat(result)
                    .extracting(Character::getId, Character::getName, Character::getCampaign)
                    .containsExactly(1L, "Aragorn", testCampaign);

            verify(campaignService).getById(campaignId);
            verify(characterRepository).save(testCharacter);
            verify(inventoryService).create(eq(savedCharacter), any(Inventory.class));
        }

        @Test
        @DisplayName("should set campaign on character before saving")
        void shouldSetCampaignOnCharacter_BeforeSaving() {
            Long campaignId = 1L;
            Character savedCharacter = createTestCharacter(1L, "Legolas", testCampaign);
            ArgumentCaptor<Character> captor = ArgumentCaptor.forClass(Character.class);

            when(campaignService.getById(campaignId)).thenReturn(testCampaign);
            when(characterRepository.save(captor.capture())).thenReturn(savedCharacter);

            characterService.createInCampaign(campaignId, testCharacter);

            assertThat(captor.getValue().getCampaign()).isEqualTo(testCampaign);
        }

        @Test
        @DisplayName("should create default inventory with correct name")
        void shouldCreateDefaultInventory_WithCorrectName() {
            Long campaignId = 1L;
            Character savedCharacter = createTestCharacter(1L, "Gimli", testCampaign);
            ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);

            when(campaignService.getById(campaignId)).thenReturn(testCampaign);
            when(characterRepository.save(any(Character.class))).thenReturn(savedCharacter);

            characterService.createInCampaign(campaignId, testCharacter);

            verify(inventoryService).create(eq(savedCharacter), captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Backpack");
        }

        @Test
        @DisplayName("should throw exception when campaign not found")
        void shouldThrowException_WhenCampaignNotFound() {
            Long wrongId = 999L;
            when(campaignService.getById(wrongId))
                    .thenThrow(new IllegalArgumentException("Campaign not found"));

            assertThatThrownBy(() -> characterService.createInCampaign(wrongId, testCharacter))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Campaign not found");

            verify(characterRepository, never()).save(any());
            verify(inventoryService,  never()).create(any(), any());
        }

        @ParameterizedTest
        @DisplayName("should handle various campaign IDs")
        @ValueSource(longs = {1L, 100L, 9999L})
        void shouldHandleVariousCampaignIds(Long campaignId) {
            Campaign campaign = createTestCampaign(campaignId, "Campaign " + campaignId);
            Character savedCharacter = createTestCharacter(1L, "Character", campaign);

            when(campaignService.getById(campaignId)).thenReturn(campaign);
            when(characterRepository.save(any(Character.class))).thenReturn(savedCharacter);

            Character result = characterService.createInCampaign(campaignId, testCharacter);

            assertThat(result).isNotNull();
            verify(campaignService).getById(campaignId);
        }
    }

    /* ───────────────────────────── Getting by ID ─────────────────────────── */

    @Nested
    @DisplayName("Getting character by ID")
    @Order(2)
    class GetByIdTests {

        @Test
        @DisplayName("should return character when exists")
        void shouldReturnCharacter_WhenExists() {
            Long id = 1L;
            Character expected = createTestCharacter(id, "Frodo", null);
            when(characterRepository.findById(id)).thenReturn(Optional.of(expected));

            Character result = characterService.getById(id);

            assertThat(result).isEqualTo(expected);
            verify(characterRepository).findById(id);
        }

        @Test
        @DisplayName("should throw exception when character not found")
        void shouldThrowException_WhenCharacterNotFound() {
            Long wrongId = 999L;
            when(characterRepository.findById(wrongId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> characterService.getById(wrongId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Character not found for ID = " + wrongId);

            verify(characterRepository).findById(wrongId);
        }

        @ParameterizedTest
        @DisplayName("should throw exception for different absent IDs")
        @ValueSource(longs = {1L, 2L, 100L, Long.MAX_VALUE})
        void shouldThrowException_ForAbsentIds(Long id) {
            when(characterRepository.findById(id)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> characterService.getById(id))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /* ─────────────────────── Getting campaign characters ─────────────────── */

    @Nested
    @DisplayName("Getting campaign characters")
    @Order(3)
    class GetCampaignCharactersTests {

        @Test
        @DisplayName("should return all characters for campaign")
        void shouldReturnAllCharacters_ForCampaign() {
            Campaign campaign = createTestCampaign(1L, "Fellowship");
            List<Character> chars = Arrays.asList(
                    createTestCharacter(1L, "Frodo", campaign),
                    createTestCharacter(2L, "Sam",   campaign));

            when(characterRepository.findByCampaign(campaign)).thenReturn(chars);

            List<Character> result = characterService.getCampaignCharacters(campaign);

            assertThat(result).hasSize(2)
                    .extracting(Character::getName)
                    .containsExactly("Frodo", "Sam");
            verify(characterRepository).findByCampaign(campaign);
        }

        @Test
        @DisplayName("should return empty list for campaign with no characters")
        void shouldReturnEmptyList_ForCampaignWithNoCharacters() {
            Campaign campaign = createTestCampaign(1L, "Empty");
            when(characterRepository.findByCampaign(campaign)).thenReturn(List.of());

            List<Character> result = characterService.getCampaignCharacters(campaign);

            assertThat(result).isEmpty();
            verify(characterRepository).findByCampaign(campaign);
        }
    }

    /* ─────────────────────────────── Get all ─────────────────────────────── */

    @Nested
    @DisplayName("Getting all characters")
    @Order(4)
    class GetAllTests {

        @Test
        @DisplayName("should return all characters from repository")
        void shouldReturnAllCharacters_FromRepository() {
            List<Character> all = Arrays.asList(
                    createTestCharacter(1L, "Hero1", null),
                    createTestCharacter(2L, "Hero2", null));

            when(characterRepository.findAll()).thenReturn(all);

            assertThat(characterService.getAll()).isEqualTo(all);
            verify(characterRepository).findAll();
        }
    }

    /* ──────────────────────────── Renaming ───────────────────────────────── */

    @Nested
    @DisplayName("Renaming character")
    @Order(5)
    class RenameTests {

        @Test
        @DisplayName("should rename character successfully when exists")
        void shouldRenameCharacterSuccessfully_WhenExists() {
            Long id = 1L;
            Character original = createTestCharacter(id, "Aragorn", null);
            Character renamed  = createTestCharacter(id, "Strider",  null);

            when(characterRepository.findById(id)).thenReturn(Optional.of(original));
            when(characterRepository.save(original)).thenReturn(renamed);

            Character result = characterService.rename(id, "Strider");

            assertThat(result.getName()).isEqualTo("Strider");
            verify(characterRepository).save(original);
        }

        @Test
        @DisplayName("should throw exception when character not found for rename")
        void shouldThrowException_WhenCharacterNotFoundForRename() {
            Long id = 999L;
            when(characterRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> characterService.rename(id, "Name"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Character not found for ID = " + id);

            verify(characterRepository, never()).save(any());
        }

        @ParameterizedTest
        @DisplayName("should handle various new names")
        @ValueSource(strings = {"Gandalf", "Boromir", "Éowyn", "Faramir"})
        void shouldHandleVariousNewNames(String name) {
            Long id = 1L;
            Character original = createTestCharacter(id, "Old", null);
            Character renamed  = createTestCharacter(id, name, null);

            when(characterRepository.findById(id)).thenReturn(Optional.of(original));
            when(characterRepository.save(original)).thenReturn(renamed);

            assertThat(characterService.rename(id, name).getName()).isEqualTo(name);
        }

        @ParameterizedTest
        @DisplayName("should handle edge case names")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t", "\n"})
        void shouldHandleEdgeCaseNames(String edge) {
            Long id = 1L;
            Character original = createTestCharacter(id, "Valid", null);

            when(characterRepository.findById(id)).thenReturn(Optional.of(original));
            when(characterRepository.save(original)).thenReturn(original);

            assertThat(characterService.rename(id, edge)).isNotNull();
            verify(characterRepository).save(original);
        }
    }

    /* ──────────────────────── Changing birth date ────────────────────────── */

    @Nested
    @DisplayName("Changing birth date")
    @Order(6)
    class ChangeBirthDateTests {

        @Test
        @DisplayName("should change birth date successfully when character exists")
        void shouldChangeBirthDateSuccessfully_WhenCharacterExists() {
            Long id = 1L;
            LocalDate newDate = LocalDate.of(2950, 3, 1);
            Character original = createTestCharacter(id, "Aragorn", null);
            Character updated  = createTestCharacter(id, "Aragorn", null);
            updated.setBirthDate(newDate);

            when(characterRepository.findById(id)).thenReturn(Optional.of(original));
            when(characterRepository.save(original)).thenReturn(updated);

            assertThat(characterService.changeBirthDate(id, newDate).getBirthDate())
                    .isEqualTo(newDate);
        }

        @Test
        @DisplayName("should throw exception when character not found for birth date change")
        void shouldThrowException_WhenCharacterNotFoundForBirthDateChange() {
            // Given
            Long id = 999L;
            LocalDate newBirthDate = LocalDate.now();
            when(characterRepository.findById(id)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> characterService.changeBirthDate(id, newBirthDate))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Character not found for ID = " + id);
        }


        @Test
        @DisplayName("should handle null birth date")
        void shouldHandleNullBirthDate() {
            Long id = 1L;
            Character original = createTestCharacter(id, "Aragorn", null);

            when(characterRepository.findById(id)).thenReturn(Optional.of(original));
            when(characterRepository.save(original)).thenReturn(original);

            assertThat(characterService.changeBirthDate(id, null)).isNotNull();
        }
    }

    /* ───────────────────────────── Deleting ──────────────────────────────── */

    @Nested
    @DisplayName("Deleting character")
    @Order(7)
    class DeleteTests {

        @Test
        @DisplayName("should delete character by ID")
        void shouldDeleteCharacter_ById() {
            characterService.delete(1L);
            verify(characterRepository).deleteById(1L);
        }

        @ParameterizedTest
        @DisplayName("should handle various IDs for deletion")
        @ValueSource(longs = {1L, 100L, 999L, Long.MAX_VALUE})
        void shouldHandleVariousIds_ForDeletion(Long id) {
            characterService.delete(id);
            verify(characterRepository).deleteById(id);
        }
    }

    /* ────────────────────── Service integration behaviour ────────────────── */

    @Nested
    @DisplayName("Service integration behavior")
    @Order(8)
    class ServiceIntegrationTests {

        @Test
        @DisplayName("should maintain transactional behavior for create operation")
        void shouldMaintainTransactionalBehavior_ForCreateOperation() {
            Long id = 1L;
            Campaign campaign = createTestCampaign(id, "Test");
            Character charToSave = createTestCharacter(null, "TestChar", null);
            Character saved = createTestCharacter(1L, "TestChar", campaign);

            when(campaignService.getById(id)).thenReturn(campaign);
            when(characterRepository.save(any())).thenReturn(saved);

            characterService.createInCampaign(id, charToSave);

            InOrder order = inOrder(campaignService, characterRepository, inventoryService);
            order.verify(campaignService).getById(id);
            order.verify(characterRepository).save(charToSave);
            order.verify(inventoryService).create(eq(saved), any());
        }

        @Test
        @DisplayName("should not create inventory if character save fails")
        void shouldNotCreateInventory_IfCharacterSaveFails() {
            Long id = 1L;
            Campaign campaign = createTestCampaign(id, "Test");
            Character charToSave = createTestCharacter(null, "TestChar", null);

            when(campaignService.getById(id)).thenReturn(campaign);
            when(characterRepository.save(any()))
                    .thenThrow(new RuntimeException("Save failed"));

            assertThatThrownBy(() -> characterService.createInCampaign(id, charToSave))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Save failed");

            verify(inventoryService, never()).create(any(), any());
        }
    }

    /* ─────────────────────────── Helper methods ─────────────────────────── */

    private Campaign createTestCampaign(Long id, String name) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private Character createTestCharacter(Long id, String name, Campaign campaign) {
        Character ch = new Character();
        ch.setId(id);
        ch.setName(name);
        ch.setCampaign(campaign);
        return ch;
    }
}
