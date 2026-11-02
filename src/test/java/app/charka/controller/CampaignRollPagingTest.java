package app.charka.controller;

import app.charka.dice.DiceService;
import app.charka.model.Campaign;
import app.charka.repository.CampaignRepository;
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

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Лог бросков кампании по страницам")
class CampaignRollPagingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private DiceService diceService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        for (int i = 1; i <= 25; i++) {
            diceService.rollForCampaign(campaign.getId(), "d6", "Бросок " + i);
        }
        entityManager.flush();
    }

    private String page(String query) throws Exception {
        return mockMvc.perform(get("/campaign/" + campaign.getId() + query))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("По 10 на странице: первая — 25…16 и «Старее»; последняя — 5…1 и «Новее»")
    void pages() throws Exception {
        String first = page("");
        assertThat(first).contains("Бросок 25<").contains("Бросок 16<").doesNotContain("Бросок 15<")
                .contains("1 из 3").contains("rolls=1").doesNotContain("← Новее");

        String last = page("?rolls=2");
        assertThat(last).contains("Бросок 5<").contains("Бросок 1<").doesNotContain("Бросок 25<")
                .contains("3 из 3").contains("← Новее").doesNotContain("Старее →");
    }

    @Test
    @DisplayName("Номер за пределами — последняя страница")
    void beyond() throws Exception {
        assertThat(page("?rolls=9")).contains("3 из 3").contains("Бросок 1<");
    }
}
