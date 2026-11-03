package app.charka.service;

import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.model.RecurringExpense;
import app.charka.repository.CampaignRepository;
import app.charka.service.campaign.CampaignTimelineService;
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
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Периодические траты (G-06)")
class RecurringExpenseServiceTest {

    private static final LocalDate START = LocalDate.of(2037, 1, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private RecurringExpenseService expenseService;
    @Autowired private CampaignTimelineService timelineService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private MoneyService moneyService;
    @Autowired private EntityManager entityManager;

    private Campaign campaign;
    private Character character;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("GENERIC");
        created.setStartDate(START);
        created.setCurrentDate(START);
        campaign = campaignRepository.save(created);
        Character added = new Character();
        added.setName("Жилец");
        character = characterService.createInCampaign(campaign.getId(), added);
        flush();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<Money> expenses() {
        return moneyService.getExpenses(character.getId()).stream().sorted(Comparator.comparing(Money::getOperationDate)).toList();
    }

    @Test
    @DisplayName("Первое списание через период; наступившие за промотку — одной строкой")
    void charges() {
        RecurringExpense rent = expenseService.create(character.getId(), "Аренда", 500, 30);
        assertThat(rent.getNextDate()).isEqualTo(START.plusDays(30));
        flush();

        timelineService.proceedDate(campaign.getId(), 29, "Неделя-другая");
        flush();
        assertThat(expenses()).isEmpty();

        timelineService.proceedDate(campaign.getId(), 40, "Ещё месяц с лишним");
        flush();
        // два периода за одну промотку — одна строка датой последнего списания
        assertThat(expenses()).singleElement().satisfies(money -> {
            assertThat(money.getAmount()).isEqualTo(-1000);
            assertThat(money.getName()).isEqualTo("Аренда ×2");
            assertThat(money.getOperationDate()).isEqualTo(START.plusDays(60));
        });
        assertThat(expenseService.getByCharacter(character.getId())).singleElement()
                .satisfies(expense -> assertThat(expense.getNextDate()).isEqualTo(START.plusDays(90)));
    }

    @Test
    @DisplayName("Сумма и период больше нуля; чужую не удалить; удалённая больше не списывается")
    void rules() {
        assertThatThrownBy(() -> expenseService.create(character.getId(), "Еда", 0, 7)).hasMessageContaining("больше нуля");
        assertThatThrownBy(() -> expenseService.create(character.getId(), "Еда", 50, 0)).hasMessageContaining("от 1 дня");

        RecurringExpense food = expenseService.create(character.getId(), "Еда", 50, 7);
        flush();
        assertThatThrownBy(() -> expenseService.delete(character.getId() + 1000, food.getId())).isInstanceOf(NotFoundException.class);

        expenseService.delete(character.getId(), food.getId());
        flush();
        timelineService.proceedDate(campaign.getId(), 30, "Месяц");
        flush();
        assertThat(expenses()).isEmpty();
    }

    @Test
    @DisplayName("Лист: трата заводится из блока денег и видна в нём")
    void sheet() throws Exception {
        mockMvc.perform(post("/character/" + character.getId() + "/expenses")
                        .param("name", "Аренда").param("amount", "500").param("periodDays", "30"))
                .andExpect(redirectedUrl("/character/" + character.getId() + "#money"));
        flush();

        String page = mockMvc.perform(get("/character/" + character.getId()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("Аренда").contains("раз в 30 дн.").contains("/expenses");
    }

    @Test
    @DisplayName("CP2020: шаблоны еды и жилья по книге, жильё — с комнатами и зоной")
    void templates() throws Exception {
        List<app.charka.system.ExpenseTemplate> templates = new app.charka.system.cp2020.Cp2020System(null, null).getExpenseTemplates();
        assertThat(templates).filteredOn(t -> t.name().equals("Kibble")).singleElement()
                .satisfies(t -> assertThat(t.amount()).isEqualTo(50));
        assertThat(templates).filteredOn(t -> t.name().equals("Apt./Condo")).singleElement().satisfies(t -> {
            assertThat(t.amount()).isEqualTo(200);
            assertThat(t.unitLabel()).isEqualTo("комн.");
            assertThat(t.getMultiplierText()).isEqualTo("Combat Zone:1|Moderate Zone:2|Corporate Zone:4|Executive Zone:6");
        });
    }
}
