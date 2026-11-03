package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.skill.SkillService;
import app.charka.system.cp2020.Cp2020IpStrategy;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Начислить всем (G-04)")
class CampaignAwardServiceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignAwardService awardService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private SkillService skillService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character solo;
    private Character netrunner;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        solo = member("Соло");
        netrunner = member("Нетраннер");
        flush();
    }

    private Character member(String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private int ip(Character character) {
        return characterRepository.findById(character.getId()).orElseThrow().getCounters().stream()
                .filter(counter -> Cp2020IpStrategy.TYPE.equals(counter.getType()))
                .map(Counter::getValue).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("В списке — только то, что можно начислять: IP, но не здоровье и не репутация")
    void options() {
        assertThat(awardService.awardableCounters(campaign.getId())).containsExactly("Очки улучшения");
    }

    @Test
    @DisplayName("Начисляет только выбранным; хватило на изученный навык — подсказка")
    void award() {
        skillService.updateLevels(solo.getId(), Map.of("handgun", 2), Map.of());
        flush();

        List<String> lines = awardService.award(campaign.getId(), "Очки улучшения", 100, List.of(solo.getId()));
        flush();

        assertThat(lines).singleElement().asString().startsWith("Соло: 0 → 100").contains("можно поднять: Пистолеты");
        assertThat(ip(solo)).isEqualTo(100);
        assertThat(ip(netrunner)).isZero();
    }

    @Test
    @DisplayName("Никого не выбрали или ноль — отказ")
    void refuse() {
        assertThatThrownBy(() -> awardService.award(campaign.getId(), "Очки улучшения", 10, List.of()))
                .hasMessageContaining("Выберите");
        assertThatThrownBy(() -> awardService.award(campaign.getId(), "Очки улучшения", 0, List.of(solo.getId())))
                .hasMessageContaining("сколько");
    }

    @Test
    @DisplayName("Страница кампании: форма с галочками игроков; итог во flash")
    void page() throws Exception {
        String page = mockMvc.perform(get("/campaign/" + campaign.getId()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("/award").contains("Очки улучшения");

        mockMvc.perform(post("/campaign/" + campaign.getId() + "/award").param("counter", "Очки улучшения").param("amount", "5")
                        .param("character", String.valueOf(solo.getId()), String.valueOf(netrunner.getId())))
                .andExpect(redirectedUrl("/campaign/" + campaign.getId() + "#award"))
                .andExpect(flash().attribute("awardResult", List.of("Соло: 0 → 5", "Нетраннер: 0 → 5")));
    }
}
