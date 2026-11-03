package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Лист с настоящими шаблонами: стандартные тесты контроллеров Thymeleaf не запускают, ошибка в шаблоне видна только здесь.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Атака на листе персонажа: шаблоны")
class AttackPageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Character thrower;
    private Character victim;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Тест");
        campaign.setSystem("CP2020");
        campaign.setStartDate(LocalDate.of(2037, 1, 1));
        campaign.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(campaign);
        thrower = member(campaign, "Метатель");
        victim = member(campaign, "Цель");
        entityManager.flush();
        entityManager.clear();
    }

    private Character member(Campaign campaign, String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private Item fromCatalog(String key, int quantity) {
        Long inventoryId = characterRepository.findById(thrower.getId()).orElseThrow().getInventories().get(0).getId();
        Item item = itemService.addFromCatalog(inventoryId, key, quantity);
        entityManager.flush();
        entityManager.clear();
        return item;
    }

    private MvcResult throwGrenade(Item grenade, int distance, String mode) throws Exception {
        return mockMvc.perform(post("/character/" + thrower.getId() + "/items/" + grenade.getId() + "/attack")
                        .param("target", String.valueOf(victim.getId()))
                        .param("distance", String.valueOf(distance))
                        .param("mode", mode))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    private String sheetAfter(MvcResult attack) throws Exception {
        return mockMvc.perform(get("/character/" + thrower.getId())
                        .flashAttrs(attack.getFlashMap()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Лист открывается без итога атаки; у гранаты режимы и дальность")
    void sheetWithoutResult() throws Exception {
        fromCatalog("grenade_fragmentation", 2);

        String page = mockMvc.perform(get("/character/" + thrower.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Метнуть", "Подорвать на месте", "data-multi=\"true\"", "data-attack-band");
    }

    @Test
    @DisplayName("Заряд мимо цели: на листе форма «Кого задело»")
    void scatterForm() throws Exception {
        Item grenade = fromCatalog("grenade_fragmentation", 2);
        when(dice.roll(anyInt())).thenReturn(2);

        String page = sheetAfter(throwGrenade(grenade, 5, "THROW"));

        assertThat(page).contains("заряд упал мимо", "Кого задело", "/attack/blast/", "/dismiss").doesNotContain("type=\"hidden\" name=\"amount\"");
    }

    @Test
    @DisplayName("Последний заряд израсходован: итог виден в блоке инвентарей, а строки предмета нет")
    void lastChargeResult() throws Exception {
        Item mine = fromCatalog("mine_all_types", 1);
        when(dice.roll(anyInt())).thenReturn(5);

        MvcResult attack = throwGrenade(mine, 0, "PLANT");

        assertThat(attack.getResponse().getRedirectedUrl()).endsWith("#inventories");
        assertThat(sheetAfter(attack)).contains("Зона 2 м, задето целей: 1").doesNotContain("id=\"attack-" + mine.getId() + "\"");
    }
}
