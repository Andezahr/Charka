package app.charka.controller;

import app.charka.exception.NotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
    @DisplayName("в окне «Добавить» у патрона список типичных значений и «Другой…»")
    void caliberList() throws Exception {
        String html = sheet();

        assertThat(html).contains("data-name-prefix=\"Патроны \"", "<option value=\"9mm\">9mm</option>", "Другой…");
    }

    @Test
    @DisplayName("секции в порядке типов: оружие, броня, вещи; пустых нет")
    void sections() throws Exception {
        plain("Фонарик", 10, null);
        itemService.addFromCatalog(inventoryId, "armor_light_jacket", 1);
        itemService.addFromCatalog(inventoryId, "arasaka_minami_10", 1);

        String html = sheet();

        assertThat(html.indexOf(">Оружие</h3>")).isPositive().isLessThan(html.indexOf(">Броня</h3>"));
        assertThat(html.indexOf(">Броня</h3>")).isLessThan(html.indexOf(">Вещи</h3>"));
        assertThat(html).doesNotContain(">Боеприпасы</h3>");
    }

    @Test
    @DisplayName("магазины и патроны к оружию не занимают свои строки и видны фишками под ним")
    void attachedToWeapon() throws Exception {
        Item weapon = itemService.addFromCatalog(inventoryId, "arasaka_minami_10", 1);
        itemService.addFromCatalog(inventoryId, "ammo_10mm_50_15", 1);
        Item magazine = itemService.addFromCatalog(inventoryId, "mag_10mm_40_caseless", 1);

        String html = sheet();

        assertThat(html).contains("id=\"item-" + weapon.getId() + "\"", "Магазины и патроны (2)", ">0/40<", ">50<")
                .doesNotContain("id=\"item-" + magazine.getId() + "\"", ">Боеприпасы</h3>", "Нечем перезарядить");
    }

    @Test
    @DisplayName("магазин без подходящего оружия остаётся своей строкой в секции боеприпасов")
    void orphanMagazine() throws Exception {
        Item magazine = itemService.addFromCatalog(inventoryId, "mag_10mm_40_caseless", 1);

        assertThat(sheet()).contains(">Боеприпасы</h3>", "id=\"item-" + magazine.getId() + "\"");
    }

    @Test
    @DisplayName("«Снарядить все» заполняет пустые магазины, итог показан у оружия")
    void loadAll() throws Exception {
        Item weapon = itemService.addFromCatalog(inventoryId, "arasaka_minami_10", 1);
        Item stack = itemService.addFromCatalog(inventoryId, "ammo_10mm_50_15", 2);
        Item first = itemService.addFromCatalog(inventoryId, "mag_10mm_40_caseless", 1);
        Item second = itemService.addFromCatalog(inventoryId, "mag_10mm_40_caseless", 1);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post(items() + "/" + weapon.getId() + "/action/LOAD_ALL").param("source", stack.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/character/" + character.getId() + "#item-" + weapon.getId()))
                .andExpect(flash().attribute("actionResult", org.hamcrest.Matchers.containsString("снаряжено магазинов 2")));
        entityManager.flush();
        entityManager.clear();

        assertThat(itemService.findById(first.getId()).getProperties()).containsEntry("ammo", "40");
        assertThat(itemService.findById(second.getId()).getProperties()).containsEntry("ammo", "40");
        assertThat(itemService.findById(stack.getId()).getQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("действие над магазином под оружием возвращает итог к оружию")
    void returnTo() throws Exception {
        Item weapon = itemService.addFromCatalog(inventoryId, "arasaka_minami_10", 1);
        Item stack = itemService.addFromCatalog(inventoryId, "ammo_10mm_50_15", 1);
        Item magazine = itemService.addFromCatalog(inventoryId, "mag_10mm_40_caseless", 1);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post(items() + "/" + magazine.getId() + "/action/LOAD")
                        .param("source", stack.getId().toString()).param("returnTo", weapon.getId().toString()))
                .andExpect(redirectedUrl("/character/" + character.getId() + "#item-" + weapon.getId()))
                .andExpect(flash().attribute("actionItemId", weapon.getId()));
    }

    @Test
    @DisplayName("одинаковые патроны складываются в одну строку с названием по патрону, разные — нет")
    void stacking() throws Exception {
        Item first = itemService.addFromCatalog(inventoryId, "ammo_9mm_50_15", 1);
        Item second = itemService.addFromCatalog(inventoryId, "ammo_9mm_100_15", 1);
        Item armorPiercing = itemService.addFromCatalog(inventoryId, "ammo_9mm_50_15_ap", 1);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getQuantity()).isEqualTo(150);
        assertThat(second.getName()).isEqualTo("Патроны 9mm");
        assertThat(second.getDescription()).isNull();
        assertThat(armorPiercing.getId()).isNotEqualTo(first.getId());
        assertThat(armorPiercing.getName()).isEqualTo("Патроны 9mm AP");
    }

    @Test
    @DisplayName("обычные вещи складываются только с тем же названием и описанием, оружие не складывается")
    void stackingOthers() {
        Item one = itemService.create(inventoryId, namedItem("Аптечка"), "GENERIC", java.util.Map.of());
        Item same = itemService.create(inventoryId, namedItem("Аптечка"), "GENERIC", java.util.Map.of());
        Item other = itemService.create(inventoryId, namedItem("Фонарик"), "GENERIC", java.util.Map.of());
        Item gun = itemService.addFromCatalog(inventoryId, "arasaka_minami_10", 1);
        Item secondGun = itemService.addFromCatalog(inventoryId, "arasaka_minami_10", 1);

        assertThat(same.getId()).isEqualTo(one.getId());
        assertThat(same.getQuantity()).isEqualTo(2);
        assertThat(other.getId()).isNotEqualTo(one.getId());
        assertThat(secondGun.getId()).isNotEqualTo(gun.getId());
    }

    private Item namedItem(String name) {
        Item item = new Item();
        item.setName(name);
        item.setQuantity(1);
        item.setCost(0);
        return item;
    }

    @Test
    @DisplayName("кнопки количества: плюс, минус; ноль — предмет кончился; чужой предмет не найден")
    void quantity() throws Exception {
        Item item = plain("Аптечка", 50, null);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post(items() + "/" + item.getId() + "/quantity").param("delta", "1"))
                .andExpect(redirectedUrl("/character/" + character.getId() + "#item-" + item.getId()));
        entityManager.flush();
        entityManager.clear();
        assertThat(itemService.findById(item.getId()).getQuantity()).isEqualTo(2);

        assertThatThrownBy(() -> itemService.changeQuantity(character.getId() + 1000, item.getId(), -1))
                .isInstanceOf(NotFoundException.class);

        mockMvc.perform(post(items() + "/" + item.getId() + "/quantity").param("delta", "-1"));
        mockMvc.perform(post(items() + "/" + item.getId() + "/quantity").param("delta", "-1"));
        entityManager.flush();
        entityManager.clear();
        assertThat(sheet()).doesNotContain("Аптечка");
    }

    @Test
    @DisplayName("после добавления возвращает к инвентарю, чужой якорь игнорируется")
    void anchor() throws Exception {
        mockMvc.perform(post(items() + "/catalog").param("catalogKey", "ammo_9mm_50_15").param("anchor", "inventory-" + inventoryId))
                .andExpect(redirectedUrl("/character/" + character.getId() + "#inventory-" + inventoryId));
        mockMvc.perform(post(items() + "/catalog").param("catalogKey", "ammo_9mm_50_15").param("anchor", "x\"><script>"))
                .andExpect(redirectedUrl("/character/" + character.getId()));
    }

    private String items() {
        return "/character/" + character.getId() + "/inventories/" + inventoryId + "/items";
    }
}
