package app.charka.system.cp2020;

import app.charka.attack.AttackRequest;
import app.charka.attack.AttackResult;
import app.charka.attack.AttackService;
import app.charka.check.OpposedCheckResult;
import app.charka.encounter.EncounterService;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CombatEffect;
import app.charka.model.EncounterParticipant;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.EncounterRepository;
import app.charka.service.CharacterService;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

/** COOL 6 у всех по умолчанию */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Репутация и Facedown CP2020 (с. 55)")
class Cp2020FacedownTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private EncounterService encounterService;
    @Autowired private AttackService attackService;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character solo;
    private Character fixer;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        solo = member("Соло");
        fixer = member("Фиксер");
        reputation(fixer, 4);
        encounterService.start(campaign.getId(), List.of(solo.getId(), fixer.getId()));
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

    private void reputation(Character character, int value) {
        Counter rep = characterRepository.findById(character.getId()).orElseThrow().getCounters().stream()
                .filter(counter -> Cp2020RepStrategy.TYPE.equals(counter.getType())).findFirst().orElseThrow();
        rep.setValue(value);
        flush();
    }

    private EncounterParticipant participant(Character character) {
        return encounterRepository.findByCampaignId(campaign.getId()).orElseThrow().getParticipants().stream()
                .filter(participant -> participant.getCharacter().getId().equals(character.getId())).findFirst().orElseThrow();
    }

    private List<String> effects(Character character) {
        return participant(character).getEffects().stream().map(CombatEffect::getLabel).toList();
    }

    @Test
    @DisplayName("Новый персонаж CP2020 получает счётчик репутации; значение от −10 до 10")
    void counter() {
        Cp2020RepStrategy strategy = new Cp2020RepStrategy();
        assertThat(strategy.getNewValue("8", 5)).isEqualTo("10");
        assertThat(strategy.getNewValue("-8", -5)).isEqualTo("-10");
        assertThat(Cp2020Facedown.reputation(characterRepository.findById(fixer.getId()).orElseThrow())).isEqualTo(4);
    }

    @Test
    @DisplayName("1D10 + COOL + REP с обеих сторон; проигравший атакует победителя с −3")
    void loserPenalty() {
        // Соло 8 + 6 + 0 = 14, Фиксер 3 + 6 + 4 = 13
        when(dice.roll(anyInt())).thenReturn(8, 3);
        OpposedCheckResult result = encounterService.contest(campaign.getId(), participant(solo).getId(), "FACEDOWN", fixer.getId());
        flush();

        assertThat(result.winner()).isEqualTo(OpposedCheckResult.Winner.INITIATOR);
        assertThat(result.opponent().result().total()).isEqualTo(13);
        assertThat(effects(fixer)).containsExactly("Facedown: уступил Соло");
        assertThat(effects(solo)).isEmpty();

        // защита 1, удар 9, урон 2
        when(dice.roll(anyInt())).thenReturn(1, 9, 2);
        AttackResult attack = attackService.attackWithAbility(fixer.getId(), "BRAWL",
                new AttackRequest(List.of(solo.getId()), null, 0, "TORSO", null, Set.of(), 0, "STRIKE", 0, 0));
        assertThat(attack.steps()).anyMatch(step -> step.contains("Уступил в Facedown"));
    }

    @Test
    @DisplayName("Равенство — ничего; трус вычитает репутацию")
    void tieAndCoward() {
        reputation(solo, -2);
        // Соло 9 + 6 − 2 = 13, Фиксер 3 + 6 + 4 = 13
        when(dice.roll(anyInt())).thenReturn(9, 3);
        OpposedCheckResult result = encounterService.contest(campaign.getId(), participant(solo).getId(), "FACEDOWN", fixer.getId());
        flush();

        assertThat(result.initiator().result().total()).isEqualTo(13);
        assertThat(effects(solo)).isEmpty();
        assertThat(effects(fixer)).isEmpty();
    }

    @Test
    @DisplayName("Трекер: кнопка Facedown в строке, итог во flash")
    void tracker() throws Exception {
        String page = mockMvc.perform(get("/campaign/" + campaign.getId() + "/encounter"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("/contest/FACEDOWN").contains("Facedown");

        when(dice.roll(anyInt())).thenReturn(2, 8);
        mockMvc.perform(post("/campaign/" + campaign.getId() + "/encounter/participants/" + participant(solo).getId() + "/contest/FACEDOWN")
                        .param("target", String.valueOf(fixer.getId())))
                .andExpect(flash().attributeExists("combatResult"));
        flush();
        assertThat(effects(solo)).containsExactly("Facedown: уступил Фиксер");
    }
}
