package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
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
@DisplayName("Лист: шапка состояния вместо вкладки «Счётчики» (R-05, R-06)")
class SheetLayoutTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
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

    private String sheet() throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mockMvc.perform(get("/character/" + character.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Здоровье, броня и статы — строками на листе, урон наносится оттуда; плиток и вкладки «Счётчики» нет")
    void stateOnSheet() throws Exception {
        String html = sheet();
        assertThat(html).contains("id=\"state-health\"", "id=\"state-armor\"", "id=\"state-stats\"", "Нанести", "id=\"counters\"", "＋ Счётчик")
                .doesNotContain("stat-label\">Инвентарей", "/counters\" class=\"\">Счётчики</a>");
        assertThat(html).doesNotContainPattern("<a[^>]*/counters\"[^>]*>Счётчики</a>");
    }

    @Test
    @DisplayName("Старый адрес счётчиков ведёт на лист; урон с листа возвращает к здоровью")
    void redirects() throws Exception {
        mockMvc.perform(get("/character/" + character.getId() + "/counters"))
                .andExpect(redirectedUrl("/character/" + character.getId() + "#counters"));
        String html = sheet();
        String damage = html.replaceAll("(?s).*action=\"(/character/" + character.getId() + "/counters/\\d+/health/damage)\".*", "$1");
        mockMvc.perform(post(damage).param("amount", "5").param("location", "TORSO").param("damageType", "NORMAL"))
                .andExpect(redirectedUrl("/character/" + character.getId() + "#state-health"));
        assertThat(sheet()).contains("Лёгкое");
    }
}
