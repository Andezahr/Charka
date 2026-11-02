package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.notes.Note;
import app.charka.model.notes.NoteCategory;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.service.notes.NoteCategoryService;
import app.charka.service.notes.NoteService;
import app.charka.system.cp2020.Cp2020Netrunning;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Импорт кампании из JSON (U-04)")
class CampaignImportServiceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private NoteService noteService;
    @Autowired private NoteCategoryService categoryService;
    @Autowired private CampaignImportService importService;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Экспорт → импорт: новая кампания с персонажем, счётчиками, программой в деке и заметкой в категории")
    void roundTrip() throws Exception {
        Campaign created = new Campaign();
        created.setName("Ночной город");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 2, 1));
        Campaign campaign = campaignRepository.save(created);
        Character character = new Character();
        character.setName("Спайдер");
        character = characterService.createInCampaign(campaign.getId(), character);
        Long inventoryId = character.getInventories().getFirst().getId();
        itemService.addFromCatalog(inventoryId, "deck-standard", 1);
        Item hammer = itemService.addFromCatalog(inventoryId, "program-hammer", 1);
        itemService.performAction(character.getId(), hammer.getId(), Cp2020Netrunning.LOAD, Map.of());
        NoteCategory category = new NoteCategory();
        category.setName("Работа");
        category = categoryService.createCategory(character.getId(), category);
        Note note = new Note();
        note.setName("Заказ");
        note.setText("Взломать Арасаку");
        note.setCharacter(character);
        note = noteService.createNote(note);
        noteService.addCategoriesToNote(note.getId(), Set.of(category.getId()));
        entityManager.flush();
        entityManager.clear();

        String json = mockMvc.perform(get("/api/campaigns/" + campaign.getId() + "/download"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String location = mockMvc.perform(multipart("/campaigns/import")
                        .file(new MockMultipartFile("file", "campaign.json", "application/json", json.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        entityManager.flush();
        entityManager.clear();

        Long importedId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
        assertThat(importedId).isNotEqualTo(campaign.getId());
        Campaign imported = campaignRepository.findById(importedId).orElseThrow();
        assertThat(imported.getCurrentDate()).isEqualTo(LocalDate.of(2037, 2, 1));
        Character copy = imported.getCharacters().getFirst();
        assertThat(copy.getName()).isEqualTo("Спайдер");
        assertThat(copy.getId()).isNotEqualTo(character.getId());
        assertThat(copy.getCounters()).isNotEmpty();

        Item deck = copy.getInventories().getFirst().getContent().stream().filter(Cp2020Netrunning::isDeck).findFirst().orElseThrow();
        Item program = copy.getInventories().getFirst().getContent().stream().filter(Cp2020Netrunning::isProgram).findFirst().orElseThrow();
        assertThat(program.getProperties().get(Cp2020Netrunning.LOADED_IN)).isEqualTo(String.valueOf(deck.getId()));

        Note copiedNote = copy.getNotes().getFirst();
        assertThat(copiedNote.getCategories()).singleElement().satisfies(found -> {
            assertThat(found.getName()).isEqualTo("Работа");
            assertThat(found.getCharacter().getId()).isEqualTo(copy.getId());
        });
    }

    @Test
    @DisplayName("Не JSON или чужая система — понятный отказ")
    void broken() {
        assertThatThrownBy(() -> importService.importCampaign("не json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> importService.importCampaign("{\"name\":\"X\",\"system\":\"NOPE\"}"))
                .hasMessageContaining("NOPE");
    }
}
