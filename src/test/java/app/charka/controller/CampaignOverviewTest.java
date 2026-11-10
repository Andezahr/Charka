package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import app.charka.service.campaign.CampaignTimelineService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("R-11: карточка «Сессия» на обзоре кампании")
class CampaignOverviewTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private CampaignTimelineService timelineService;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("Промотка, новая сессия и начисление — одной карточкой; хроника новыми сверху, старше пятой — свёрнуты")
    void session() throws Exception {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        Campaign campaign = campaignRepository.save(created);
        Character character = new Character();
        character.setName("Колясик");
        characterService.createInCampaign(campaign.getId(), character);
        for (int i = 1; i <= 7; i++) {
            timelineService.proceedDate(campaign.getId(), 1, "День " + i);
        }
        mockMvc.perform(post("/campaign/" + campaign.getId() + "/chronicles").param("name", "День 8").param("duration", "1"))
                .andExpect(redirectedUrl("/campaign/" + campaign.getId() + "#session"));
        entityManager.flush();
        entityManager.clear();

        String page = mockMvc.perform(get("/campaign/" + campaign.getId())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String session = page.substring(page.indexOf("id=\"session\""), page.indexOf("id=\"chronicle\""));
        assertThat(session).contains("2037-01-09", "⏩ Промотать", "🍀 Новая сессия", "id=\"award\"", "Начислить", "Даунтайм");
        String chronicle = page.substring(page.indexOf("id=\"chronicle\""));
        assertThat(chronicle.indexOf("День 8")).isLessThan(chronicle.indexOf("День 4"));
        assertThat(chronicle).contains("Раньше: ещё 3");
        assertThat(chronicle.indexOf("День 3")).isGreaterThan(chronicle.indexOf("Раньше: ещё 3"));
    }
}
