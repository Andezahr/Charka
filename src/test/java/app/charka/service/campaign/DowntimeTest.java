package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.model.RecurringExpense;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.MoneyService;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.SkillService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Даунтайм одной формой (A-10)")
class DowntimeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private SkillService skillService;
    @Autowired private MoneyService moneyService;
    @Autowired private HealthCounterService healthCounterService;
    @Autowired private DowntimeService downtimeService;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character solo;
    private Character wounded;
    private Character medic;
    private Character idle;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        solo = member("Колясик", "solo", "combat_sense", 6);
        wounded = member("Олежа", "solo", "combat_sense", 4);
        medic = member("Доктор", "medtechie", "medical_tech", 7);
        idle = member("Бустер", null, null, 0);
        clear();
        healthCounterService.applyDamage(wounded.getId(), app.charka.model.counters.strategy.health.DamageRequest.builder()
                .amount(10).location("TORSO").build());
        RecurringExpense rent = new RecurringExpense();
        rent.setName("Квартира");
        rent.setAmount(100L);
        rent.setPeriodDays(7);
        rent.setNextDate(LocalDate.of(2037, 1, 5));
        Character owner = characterRepository.findById(solo.getId()).orElseThrow();
        rent.setCharacter(owner);
        owner.getRecurringExpenses().add(rent);
        clear();
    }

    private Character member(String name, String role, String special, int level) {
        Character character = new Character();
        character.setName(name);
        Character created = characterService.createInCampaign(campaign.getId(), character);
        if (role != null) {
            skillService.setRoles(created.getId(), List.of(role));
            skillService.updateLevels(created.getId(), Map.of(special, level), Map.of());
        }
        return created;
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Форма: работа с месячной ставкой у ролей, раненому предложен лучший медик")
    void form() throws Exception {
        String page = mockMvc.perform(get("/campaign/" + campaign.getId() + "/downtime"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Работал · 3000/мес", "Работал · 5000/мес", "ранен", "name=\"healer." + wounded.getId() + "\"");
        DowntimeService.Row row = downtimeService.form(campaign.getId()).stream()
                .filter(candidate -> candidate.character().getId().equals(wounded.getId())).findFirst().orElseThrow();
        assertThat(row.healer()).isEqualTo(medic.getId());
        assertThat(downtimeService.form(campaign.getId())).filteredOn(candidate -> candidate.character().getId().equals(idle.getId()))
                .singleElement().satisfies(candidate -> assertThat(candidate.earning()).isNull());
    }

    @Test
    @DisplayName("Неделя на 4 персонажа одним нажатием: зарплата за 7 дней, медик, траты, одна запись хроники; итог можно отменить")
    void week() throws Exception {
        when(dice.roll(anyInt())).thenReturn(10, 1);

        MvcResult result = mockMvc.perform(post("/campaign/" + campaign.getId() + "/downtime")
                        .param("days", "7")
                        .param("worked", String.valueOf(solo.getId()), String.valueOf(medic.getId()))
                        .param("healer." + wounded.getId(), String.valueOf(medic.getId())))
                .andExpect(status().is3xxRedirection()).andReturn();
        clear();

        @SuppressWarnings("unchecked")
        List<String> report = (List<String>) result.getFlashMap().get("downtimeReport");
        assertThat(report).anyMatch(line -> line.startsWith("Колясик: Работа: Соло, спецспособность 6 (7 дн.) +700"))
                .anyMatch(line -> line.startsWith("Доктор: Работа: Медтехник, спецспособность 7 (7 дн.) +1166"))
                .anyMatch(line -> line.startsWith("Олежа: "));
        assertThat(moneyService.getByCharacter(solo.getId())).extracting(Money::getAmount).contains(700L, -100L);
        assertThat(moneyService.getByCharacter(idle.getId())).isEmpty();
        Campaign after = campaignRepository.findById(campaign.getId()).orElseThrow();
        assertThat(after.getCurrentDate()).isEqualTo(LocalDate.of(2037, 1, 8));
        assertThat(after.getChronicles()).singleElement().satisfies(chronicle -> assertThat(chronicle.getName()).isEqualTo("Прошло 7 дн."));
        assertThat(result.getFlashMap().get(app.charka.undo.UndoInterceptor.OFFER)).isNotNull();
    }
}
