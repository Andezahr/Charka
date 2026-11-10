package app.charka.controller;

import app.charka.effect.EffectTemplate.DurationUnit;
import app.charka.effect.TimedEffectService;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import app.charka.service.MoneyService;
import jakarta.persistence.EntityManager;
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

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Экран мастера (U-01)")
class GmScreenControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private MoneyService moneyService;
    @Autowired private TimedEffectService effectService;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Все персонажи CP2020 на обзоре: состояние, SP, статы, деньги, эффекты, удача; /gm ведёт туда")
    void screen() throws Exception {
        Campaign created = new Campaign();
        created.setName("Ночной город");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        Campaign campaign = campaignRepository.save(created);
        Character character = new Character();
        character.setName("Колясик");
        character = characterService.createInCampaign(campaign.getId(), character);
        Money money = new Money();
        money.setName("Гонорар");
        money.setAmount(750L);
        moneyService.create(character.getId(), money);
        effectService.create(character.getId(), "Boost", "int", 1, "3", DurationUnit.HOUR, null);
        entityManager.flush();
        entityManager.clear();

        // R-10: экран мастера — список обзора кампании, старый адрес ведёт туда
        mockMvc.perform(get("/campaign/" + campaign.getId() + "/gm"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/campaign/" + campaign.getId()));
        String page = mockMvc.perform(get("/campaign/" + campaign.getId()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(page).contains("Колясик").contains("SP голова 0").contains("750").contains("Boost 3 ч")
                .contains("🍀 6").contains("INT 7*").contains("Открыть лист").doesNotContain(">Мастер</a>");
    }
}
