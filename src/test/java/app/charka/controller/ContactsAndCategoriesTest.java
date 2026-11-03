package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Contact;
import app.charka.model.notes.NoteCategory;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.notes.NoteCategoryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Контакты (P-07) и категории заметок на персонажа (U-05)")
class ContactsAndCategoriesTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private NoteCategoryService categoryService;
    @Autowired private EntityManager entityManager;

    private Character first;
    private Character second;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        Campaign campaign = campaignRepository.save(created);
        first = member(campaign, "Первый");
        second = member(campaign, "Второй");
        flush();
    }

    private Character member(Campaign campaign, String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("Контакты")
    class Contacts {

        @Test
        @DisplayName("Добавить, поправить долг, удалить; видны на странице заметок")
        void lifecycle() throws Exception {
            mockMvc.perform(post("/character/" + first.getId() + "/contacts")
                    .param("name", "Рог").param("role", "фиксер").param("relation", "FRIEND"));
            flush();
            Contact contact = characterRepository.findById(first.getId()).orElseThrow().getContacts().getFirst();
            assertThat(contact.getRelation()).isEqualTo(Contact.Relation.FRIEND);

            mockMvc.perform(post("/character/" + first.getId() + "/contacts/" + contact.getId() + "/edit")
                    .param("name", "Рог").param("relation", "FRIEND").param("owes", "должен мне 500"));
            flush();
            String page = mockMvc.perform(get("/character/" + first.getId() + "/notes"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(page).contains("id=\"contacts\"").contains("Рог").contains("должен мне 500");

            mockMvc.perform(post("/character/" + first.getId() + "/contacts/" + contact.getId() + "/delete"));
            flush();
            assertThat(characterRepository.findById(first.getId()).orElseThrow().getContacts()).isEmpty();
        }

        @Test
        @DisplayName("Чужой контакт не правится: 404")
        void foreign() throws Exception {
            mockMvc.perform(post("/character/" + first.getId() + "/contacts").param("name", "Рог"));
            flush();
            Long id = characterRepository.findById(first.getId()).orElseThrow().getContacts().getFirst().getId();

            mockMvc.perform(post("/character/" + second.getId() + "/contacts/" + id + "/delete"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Категории")
    class Categories {

        @Test
        @DisplayName("Категория видна только своему персонажу; чужую не удалить")
        void owned() throws Exception {
            mockMvc.perform(post("/character/" + first.getId() + "/categories").param("name", "Враги"));
            flush();

            assertThat(categoryService.getForCharacter(first.getId())).extracting(NoteCategory::getName).contains("Враги");
            assertThat(categoryService.getForCharacter(second.getId())).extracting(NoteCategory::getName).doesNotContain("Враги");

            Long id = categoryService.getForCharacter(first.getId()).stream()
                    .filter(category -> category.getName().equals("Враги")).findFirst().orElseThrow().getId();
            mockMvc.perform(post("/character/" + second.getId() + "/categories/" + id + "/delete"))
                    .andExpect(status().isNotFound());
        }
    }
}
