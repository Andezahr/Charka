package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import app.charka.support.CharkaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CharkaTest
@Transactional
@DisplayName("Тема оформления по системе кампании (PH-21)")
class ThemeInterceptorTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"KOSMOZEKI, theme-kosmozeki", "CP2020, theme-cp2020", "GENERIC, theme-general"})
    @DisplayName("страницы кампании и персонажа получают класс темы системы на body")
    void themeClassOnBody(String system, String themeClass) throws Exception {
        Campaign campaign = campaign(system);
        Character character = new Character();
        character.setName("Шнырь");
        Long characterId = characterService.createInCampaign(campaign.getId(), character).getId();

        mockMvc.perform(get("/campaign/" + campaign.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("<body class=\"" + themeClass + "\">")));
        mockMvc.perform(get("/character/" + characterId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("<body class=\"" + themeClass + "\">")));
    }

    @Test
    @DisplayName("вне кампании класса темы нет — фирменный неон")
    void noThemeOutsideCampaign() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<body>")));
    }

    private Campaign campaign(String system) {
        Campaign campaign = new Campaign();
        campaign.setName("Зона");
        campaign.setSystem(system);
        campaign.setStartDate(LocalDate.of(2037, 1, 1));
        campaign.setCurrentDate(LocalDate.of(2037, 1, 1));
        return campaignRepository.save(campaign);
    }
}
