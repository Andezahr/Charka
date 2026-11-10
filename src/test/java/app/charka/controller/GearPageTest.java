package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("R-08: носимые инвентари на листе, всё — на вкладке «Снаряжение»")
class GearPageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private EntityManager entityManager;

    private Character character;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Тест");
        campaign.setSystem("CP2020");
        campaign.setStartDate(LocalDate.of(2037, 1, 1));
        campaign.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(campaign);
        Character created = new Character();
        created.setName("Колясик");
        character = characterService.createInCampaign(campaign.getId(), created);
        entityManager.flush();
        entityManager.clear();
    }

    private String page(String path) throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mockMvc.perform(get("/character/" + character.getId() + path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Inventory inventory(String name) {
        return characterService.getById(character.getId()).getInventories().stream()
                .filter(inventory -> inventory.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("У нового персонажа «На теле» и «В сумке» — на листе; вкладка «Снаряжение» в шапке")
    void defaults() throws Exception {
        String sheet = page("");
        assertThat(sheet).contains("Носимое", "На теле", "В сумке", "Всё снаряжение").containsPattern("/gear\"[^>]*>Снаряжение</a>");
        assertThat(page("/gear")).contains("Инвентари", "на листе", "Убрать с листа", "Инвентарь: в машине");
    }

    @Test
    @DisplayName("Неносимый инвентарь и оружие в нём — только в «Снаряжении»; «Носить» переносит его на лист")
    void notCarried() throws Exception {
        mockMvc.perform(post("/character/" + character.getId() + "/inventories").param("name", "В машине").param("carried", "false"));
        Inventory car = inventory("В машине");
        itemService.addFromCatalog(car.getId(), "militech_arms_avenger", 1);

        String sheet = page("");
        assertThat(sheet).doesNotContain("В машине", "Militech Arms Avenger");
        assertThat(page("/gear")).contains("В машине", "не носится", "Militech Arms Avenger", "Носить: на лист");

        mockMvc.perform(post("/character/" + character.getId() + "/inventories/" + car.getId() + "/carried").param("carried", "true"))
                .andExpect(redirectedUrl("/character/" + character.getId() + "/gear#inventory-" + car.getId()));
        assertThat(page("")).contains("В машине", "Militech Arms Avenger");
    }

    @Test
    @DisplayName("Деньги на листе — три последние записи и ссылка на журнал; в «Снаряжении» — все")
    void money() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/character/" + character.getId() + "/money").param("name", "Запись " + i).param("amount", String.valueOf(i)));
        }
        String sheet = page("");
        assertThat(sheet).contains("Запись 5", "Запись 4", "Запись 3", "Весь журнал (5)").doesNotContain("Запись 2", "Запись 1");
        assertThat(page("/gear")).contains("Запись 1", "Запись 5", "Регулярная трата");
    }

    @Test
    @DisplayName("Действие со «Снаряжения» возвращает туда же, с листа — на лист")
    void returnsToGear() throws Exception {
        String base = "http://localhost/character/" + character.getId();
        mockMvc.perform(post("/character/" + character.getId() + "/money").param("name", "Пиво").param("amount", "-5")
                        .header("Referer", base + "/gear"))
                .andExpect(redirectedUrl("/character/" + character.getId() + "/gear"));
        mockMvc.perform(post("/character/" + character.getId() + "/money").param("name", "Пиво").param("amount", "-5")
                        .header("Referer", base))
                .andExpect(redirectedUrl("/character/" + character.getId()));
    }
}
