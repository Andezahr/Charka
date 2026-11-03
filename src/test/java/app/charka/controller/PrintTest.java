package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.SkillService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Лист на печать (A-12)")
class PrintTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private SkillService skillService;
    @Autowired private ItemService itemService;
    @Autowired private HealthCounterService healthCounterService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character solo;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Последний шанс");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        Character character = new Character();
        character.setName("Колясик");
        solo = characterService.createInCampaign(campaign.getId(), character);
        Character npc = new Character();
        npc.setName("Бандит");
        npc.setNpc(true);
        characterService.createInCampaign(campaign.getId(), npc);
        skillService.setRoles(solo.getId(), List.of("solo"));
        skillService.updateLevels(solo.getId(), Map.of("combat_sense", 6, "handgun", 5), Map.of());
        entityManager.flush();
        entityManager.clear();
        Long inventory = characterRepository.findById(solo.getId()).orElseThrow().getInventories().get(0).getId();
        itemService.addFromCatalog(inventory, "arasaka_minami_10", 1);
        itemService.addFromCatalog(inventory, "armor_light_jacket", 1);
        healthCounterService.applyDamage(solo.getId(), app.charka.model.counters.strategy.health.DamageRequest.builder()
                .amount(6).location("R_LEG").build());
        entityManager.flush();
        entityManager.clear();
    }

    private String page(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Лист персонажа: статы, производные, броня по зонам, навыки с итогом, трек здоровья с отмеченным уроном, оружие с полями")
    void character() throws Exception {
        String html = page("/character/" + solo.getId() + "/print");

        assertThat(html).contains("@page { size: A4", "Колясик", "Соло", "Последний шанс",
                "Характеристики", "Производные", "Бег за действие", "BTM", "Броня", "Голова (1)", "SP ",
                "Навыки", "Боевое чутьё", "Здоровье", "Лёгкое", "Смертельное 6", "box is-filled",
                "Оружие", "Arasaka Minami 10 · ", "Броня", "Заметки");
        // 6 урона в ногу минус BTM — сколько записано в здоровье, столько клеток закрашено
        int damage = healthCounterService.healthCounter(solo.getId()).orElseThrow().getValue();
        assertThat(damage).isPositive();
        assertThat(html.split("box is-filled", -1).length - 1).isEqualTo(damage);
        assertThat(html).doesNotContain("topbar", "app.js");
    }

    @Test
    @DisplayName("Мастер: все персонажи игроков подряд, каждый с новой страницы; NPC — по просьбе")
    void campaign() throws Exception {
        String html = page("/campaign/" + campaign.getId() + "/print");
        assertThat(html).contains("Колясик").doesNotContain("Бандит");
        assertThat(page("/campaign/" + campaign.getId() + "/print?npc=true")).contains("Колясик", "Бандит");
        assertThat(page("/character/" + solo.getId())).contains("/character/" + solo.getId() + "/print", "🖨 Печать");
        assertThat(page("/campaign/" + campaign.getId())).contains("🖨 Распечатать всех");
    }
}
