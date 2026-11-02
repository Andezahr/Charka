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
    @DisplayName("Первая страница: 20 новых, ссылка «Старее»; вторая: оставшиеся 5, ссылка «Новее»")
    void pages() throws Exception {
        String first = page("");
        assertThat(first).contains("Бросок 25").contains("Бросок 6").doesNotContain("Бросок 5<")
                .contains("1 из 2").contains("rolls=1").doesNotContain("← Новее");

        String second = page("?rolls=1");
        assertThat(second).contains("Бросок 5<").contains("Бросок 1<").doesNotContain("Бросок 25")
                .contains("2 из 2").contains("← Новее").doesNotContain("Старее →");
    }

    @Test
    @DisplayName("Номер за пределами — последняя страница")
    void beyond() throws Exception {
        assertThat(page("?rolls=9")).contains("2 из 2").contains("Бросок 1<");
    }
}
