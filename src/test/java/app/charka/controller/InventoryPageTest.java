package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Инвентарь на листе с настоящими шаблонами.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Инвентарь на листе персонажа: шаблоны")
class InventoryPageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private EntityManager entityManager;

    private Character character;
    private Long inventoryId;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Тест");
        campaign.setSystem("CP2020");
        campaign.setStartDate(LocalDate.of(2037, 1, 1));
        campaign.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(campaign);
        Character created = new Character();
        created.setName("Соло");
        character = characterService.createInCampaign(campaign.getId(), created);
        inventoryId = character.getInventories().get(0).getId();
    }

    private Item plain(String name, int cost, String description) {
        Item item = new Item();
        item.setName(name);
        item.setQuantity(1);
        item.setCost(cost);
        item.setDescription(description);
        return itemService.create(inventoryId, item);
    }

    private String sheet() throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mockMvc.perform(get("/character/" + character.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String deleteForm(String html, Item item) {
        String action = "/items/" + item.getId() + "/delete";
        int start = html.lastIndexOf("<form", html.indexOf(action));
        return html.substring(start, html.indexOf("</form>", start));
    }

    @Test
    @DisplayName("обычный предмет без цены и описания удаляется одним нажатием на строке")
    void disposableItem() throws Exception {
        Item item = plain("Зажигалка", 0, null);

        assertThat(deleteForm(sheet(), item)).contains("icon-btn danger").doesNotContain("data-confirm");
    }

    @Test
    @DisplayName("оружие, боеприпасы, предмет с ценой или описанием — только из панели правки и с подтверждением")
    void protectedItems() throws Exception {
        Item ammo = itemService.addFromCatalog(inventoryId, "ammo_9mm_50_15", 1);
        Item priced = plain("Кибердека", 1000, null);
        Item described = plain("Письмо", 0, "От заказчика");

        String html = sheet();

        assertThat(java.util.List.of(ammo, priced, described)).allSatisfy(item ->
                assertThat(deleteForm(html, item)).contains("Удалить предмет", "data-confirm=\"Удалить «" + item.getName() + "»")
                        .doesNotContain("icon-btn"));
    }

    @Test
    @DisplayName("в форме предмета у патрона список типичных значений и «Другой…»")
    void caliberList() throws Exception {
        String html = sheet();

        assertThat(html).contains("data-name-prefix=\"Патроны \"", "<option value=\"9mm\">9mm</option>", "Другой…");
    }
}
