package app.charka.controller;

import app.charka.encounter.EncounterService;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CombatEffect;
import app.charka.model.EncounterParticipant;
import app.charka.model.Item;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Трекер с настоящими шаблонами: стандартные тесты контроллеров Thymeleaf не запускают.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Страница боя: шаблоны")
class EncounterPageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private EncounterService encounterService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character shooter;
    private Character victim;
    private String base;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Тест");
        campaign.setSystem("CP2020");
        campaign.setStartDate(LocalDate.of(2037, 1, 1));
        campaign.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(campaign);
        shooter = member("Стрелок");
        victim = member("Цель");
        base = "/campaign/" + campaign.getId() + "/encounter";
    }

    private Character member(String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private String page(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void fight() {
        encounterService.start(campaign.getId(), List.of(shooter.getId(), victim.getId()));
        flushAndClear();
    }

    @Test
    @DisplayName("без боя: все отмечены, бой начинается одним нажатием")
    void setupPage() throws Exception {
        flushAndClear();

        String html = page(base);

        assertThat(html).contains("Начать бой").contains("Стрелок").contains("Цель");
        assertThat(html.split("checked=\"checked\"").length - 1).isEqualTo(2);
        assertThat(page("/campaign/" + campaign.getId())).contains("⚔ Бой");
        assertThat(page("/character/" + shooter.getId())).doesNotContain("⚔ Бой");
    }

    @Test
    @DisplayName("перезарядка есть в строке участника: фишек в трекере нет; без запаса кнопки нет")
    void reloadInTracker() throws Exception {
        Long inventory = characterRepository.findById(shooter.getId()).orElseThrow().getInventories().get(0).getId();
        Item rpg = itemService.addFromCatalog(inventory, "militech_arms_rpg_a", 1);
        flushAndClear();
        fight();
        assertThat(page(base)).doesNotContain("/items/" + rpg.getId() + "/action/RELOAD");

        Item round = itemService.addFromCatalog(inventory, "ammo_rpg", 1);
        flushAndClear();
        // РПГ из справочника заряжен: запас предлагается, только когда он пуст
        mockMvc.perform(post("/character/" + shooter.getId() + "/inventories/" + inventory + "/items/" + rpg.getId() + "/action/FIRE"));
        flushAndClear();

        assertThat(page(base)).contains("/items/" + rpg.getId() + "/action/RELOAD").contains("value=\"" + round.getId() + "\"");
    }

    @Test
    @DisplayName("эффект виден у участника, снимается нажатием; в диалоге ослеплённого отмечен штраф")
    void effectChip() throws Exception {
        fight();
        EncounterParticipant shooterRow = encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .filter(row -> row.character().getId().equals(shooter.getId())).findFirst().orElseThrow().participant();
        CombatEffect effect = new CombatEffect();
        effect.setParticipant(shooterRow);
        effect.setLabel("Ослеплён −3");
        effect.setRoundsLeft(4);
        effect.setAttackOption("blinded");
        shooterRow.getEffects().add(effect);
        Long inventory = characterRepository.findById(shooter.getId()).orElseThrow().getInventories().get(0).getId();
        itemService.addFromCatalog(inventory, "arasaka_minami_10", 1);
        flushAndClear();

        String html = page(base);

        assertThat(html).contains("Ослеплён −3 · раундов 4").contains("/effects/");
        assertThat(html).containsPattern("value=\"blinded\"\s+checked");
    }

    @Test
    @DisplayName("начало боя: очередь, главная кнопка и вкладка на листе")
    void startFromForm() throws Exception {
        flushAndClear();
        mockMvc.perform(post(base + "/start").param("character", shooter.getId().toString()).param("character", victim.getId().toString()))
                .andExpect(redirectedUrl(base));
        flushAndClear();

        String html = page(base);

        assertThat(html).contains("Раунд 1").contains("Следующий ход").contains("Ход: ").contains("combatant-" + shooter.getId());
        assertThat(page("/campaign/" + campaign.getId())).contains("Идёт бой · раунд 1");
        assertThat(page("/character/" + shooter.getId())).contains("⚔ Бой");
    }

    @Test
    @DisplayName("атака из строки участника возвращается в трекер с итогом в этой строке")
    void attackReturnsToTracker() throws Exception {
        Long inventoryId = characterRepository.findById(shooter.getId()).orElseThrow().getInventories().getFirst().getId();
        Item grenade = itemService.addFromCatalog(inventoryId, "grenade_fragmentation", 2);
        fight();
        assertThat(page(base)).contains("name=\"returnCampaign\"").contains("attack-" + grenade.getId());

        MvcResult attack = mockMvc.perform(post("/character/" + shooter.getId() + "/items/" + grenade.getId() + "/attack")
                        .param("target", victim.getId().toString())
                        .param("distance", "5")
                        .param("mode", "THROW")
                        .param("returnCampaign", campaign.getId().toString()))
                .andExpect(redirectedUrl(base + "#combatant-" + shooter.getId()))
                .andReturn();

        String html = mockMvc.perform(get(base).flashAttrs(attack.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains(grenade.getName() + " → ").contains("item-result");
    }

    @Test
    @DisplayName("урон: итог с расчётом в строке цели, переход к ней")
    void damageShowsSteps() throws Exception {
        fight();
        Long participantId = encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .filter(row -> row.character().getId().equals(victim.getId())).findFirst().orElseThrow().participant().getId();

        MvcResult damage = mockMvc.perform(post(base + "/participants/" + participantId + "/damage")
                        .param("amount", "5").param("location", "TORSO"))
                .andExpect(redirectedUrl(base + "#combatant-" + victim.getId()))
                .andReturn();

        String html = mockMvc.perform(get(base).flashAttrs(damage.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Зона попадания").contains("Урон 5");
    }

    @Test
    @DisplayName("следующий ход ведёт к строке того, кто ходит; конец боя — на страницу кампании")
    void nextAndEnd() throws Exception {
        fight();

        mockMvc.perform(post(base + "/next")).andExpect(status().is3xxRedirection());
        flushAndClear();
        assertThat(page(base)).contains("Раунд 1");

        mockMvc.perform(post(base + "/end")).andExpect(redirectedUrl("/campaign/" + campaign.getId()));
        flushAndClear();
        assertThat(page(base)).contains("Начать бой");
    }

    @Test
    @DisplayName("бой уже закончен в другой вкладке: сообщение, а не падение")
    void nextWithoutFight() throws Exception {
        flushAndClear();
        MvcResult next = mockMvc.perform(post(base + "/next")).andExpect(redirectedUrl(base)).andReturn();

        String html = mockMvc.perform(get(base).flashAttrs(next.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Бой уже закончен");
    }
}
