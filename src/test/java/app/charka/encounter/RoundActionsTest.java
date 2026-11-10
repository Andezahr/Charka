package app.charka.encounter;

import app.charka.attack.AttackRequest;
import app.charka.attack.AttackResult;
import app.charka.attack.AttackService;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("Штраф за несколько действий в раунде (A-05)")
class RoundActionsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private AttackService attackService;
    @Autowired private EncounterService encounterService;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character shooter;
    private Character victim;
    private Item minami;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        shooter = member("Стрелок");
        victim = member("Цель");
        clear();
        Long inventory = characterRepository.findById(shooter.getId()).orElseThrow().getInventories().get(0).getId();
        minami = itemService.addFromCatalog(inventory, "arasaka_minami_10", 1);
        clear();
    }

    private Character member(String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    private AttackResult shoot(Set<String> options) {
        AttackResult result = attackService.attack(shooter.getId(), minami.getId(),
                new AttackRequest(victim.getId(), null, 5, "TORSO", null, options, 0));
        clear();
        return result;
    }

    private EncounterView.Row row() {
        return encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .filter(row -> row.character().getId().equals(shooter.getId())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Выстрел → выстрел: второе действие −3, третье −6, диалог ставит сам; новый раунд — с нуля")
    void growsAndResets() throws Exception {
        encounterService.start(campaign.getId(), List.of(shooter.getId(), victim.getId()));
        clear();
        when(dice.roll(anyInt())).thenReturn(5);
        assertThat(row().actionsDone()).isZero();
        assertThat(row().attackOptions()).doesNotContain("action_2");

        shoot(Set.of());
        assertThat(row().actionsDone()).isEqualTo(1);
        assertThat(row().attackOptions()).contains("action_2");

        AttackResult second = shoot(Set.of("action_2"));
        assertThat(second.steps()).anyMatch(step -> step.contains("2-е действие -3"));
        assertThat(row().actionOption()).isEqualTo("action_3");

        String page = mockMvc.perform(get("/campaign/" + campaign.getId() + "/encounter"))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Действий: 2").containsPattern("<option value=\"action_3\"\\s+selected=\"selected\">3-е действие −6</option>");

        encounterService.next(campaign.getId());
        encounterService.next(campaign.getId());
        clear();
        assertThat(encounterService.view(campaign.getId()).orElseThrow().encounter().getRound()).isEqualTo(2);
        assertThat(row().actionsDone()).isZero();
    }

    @Test
    @DisplayName("Проверка навыка из трекера и перезарядка — тоже действия; вне боя не считается")
    void otherActions() {
        shoot(Set.of());
        encounterService.start(campaign.getId(), List.of(shooter.getId(), victim.getId()));
        clear();
        assertThat(row().actionsDone()).as("выстрел до боя не считается").isZero();

        encounterService.check(campaign.getId(), row().participant().getId(), "awareness", null, 0);
        clear();
        Long inventory = characterRepository.findById(shooter.getId()).orElseThrow().getInventories().get(0).getId();
        Item magazine = itemService.addFromCatalog(inventory, "mag_10mm_40_caseless", 1);
        Item ammo = itemService.addFromCatalog(inventory, "ammo_10mm_50_15", 1);
        clear();
        itemService.performAction(shooter.getId(), minami.getId(), "LOAD_ALL", Map.of("source", String.valueOf(ammo.getId())));
        clear();
        itemService.performAction(shooter.getId(), minami.getId(), "FIRE", Map.of());
        clear();
        assertThat(row().actionsDone()).as("снарядить магазин и «Списать патрон» — учёт, не действие раунда").isEqualTo(1);
        itemService.performAction(shooter.getId(), minami.getId(), "RELOAD", Map.of("source", String.valueOf(magazine.getId())));
        clear();

        assertThat(row().actionsDone()).isEqualTo(2);
    }
}
