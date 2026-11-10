package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.repository.CampaignRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Справка правил под рукой (A-11)")
class ReferenceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;

    private Campaign campaign(String system) {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem(system);
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        return campaignRepository.save(created);
    }

    private String get200(String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("CP2020: сложности, дальность, модификаторы, действия, урон, лечение, фамблы — числа из правил, со страницами")
    void cp2020() throws Exception {
        Campaign campaign = campaign("CP2020");

        String reference = get200("/campaign/" + campaign.getId() + "/reference");

        assertThat(reference).contains("Сложность проверки", "с. 41", "Средне", ">15<",
                "Дальность", "Модификаторы атаки", "с. 99", "Цель неподвижна", ">+4<", "Засада", ">+5<",
                "Действия в раунде", "с. 98", "Порядок урона", "Лечение", "Фамблы", "с. 43", "5 оружие выпало из рук");
        assertThat(get200("/campaign/" + campaign.getId())).contains("data-reference-open", "/campaign/" + campaign.getId() + "/reference");
        assertThat(get200("/campaign/" + campaign.getId() + "/encounter")).contains("data-reference-open");
    }

    @Test
    @DisplayName("Система без справки — пусто")
    void generic() throws Exception {
        assertThat(get200("/campaign/" + campaign("GENERIC").getId() + "/reference")).contains("У этой системы справки нет");
    }
}
