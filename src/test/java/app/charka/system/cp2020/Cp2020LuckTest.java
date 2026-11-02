package app.charka.system.cp2020;

import app.charka.attack.AttackRequest;
import app.charka.attack.AttackResult;
import app.charka.attack.AttackService;
import app.charka.encounter.EncounterService;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.skill.SkillCheck;
import app.charka.skill.SkillService;
import app.charka.system.GameSystemRegistry;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** LUCK 6 у всех по умолчанию */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Удача CP2020 (с. 26)")
class Cp2020LuckTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private SkillService skillService;
    @Autowired private AttackService attackService;
    @Autowired private EncounterService encounterService;
    @Autowired private GameSystemRegistry registry;
    @Autowired private EntityManager entityManager;
    @Autowired private app.charka.repository.EncounterRepository encounterRepository;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character lucky;
    private Character other;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        lucky = member("Везунчик");
        other = member("Другой");
        flush();
        when(dice.roll(anyInt())).thenReturn(5);
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

    private int luck() {
        return registry.get("CP2020").getLuck(characterRepository.findById(lucky.getId()).orElseThrow());
    }

    @Test
    @DisplayName("Проверка: удача прибавляется и тратится; больше, чем есть, — отказ")
    void check() {
        SkillCheck check = skillService.check(lucky.getId(), "awareness", null, 0, null, 4);
        flush();

        assertThat(check.result().steps()).anyMatch(step -> step.contains("Удача +4"));
        assertThat(luck()).isEqualTo(2);
        assertThatThrownBy(() -> skillService.check(lucky.getId(), "awareness", null, 0, null, 3))
                .hasMessageContaining("осталось 2");
    }

    @Test
    @DisplayName("Атака: удача — к броску атакующего")
    void attack() {
        encounterService.start(campaign.getId(), List.of(lucky.getId(), other.getId()));
        flush();

        AttackResult result = attackService.attackWithAbility(lucky.getId(), "BRAWL",
                new AttackRequest(List.of(other.getId()), null, 0, "TORSO", null, Set.of(), 0, "STRIKE", 0, 0, 3));
        flush();

        assertThat(result.steps()).anyMatch(step -> step.contains("Удача +3"));
        assertThat(luck()).isEqualTo(3);
    }

    @Test
    @DisplayName("«Новая сессия» возвращает удачу всем; поле удачи видно в диалогах")
    void session() throws Exception {
        skillService.check(lucky.getId(), "awareness", null, 0, null, 6);
        flush();
        assertThat(luck()).isZero();

        String skills = mockMvc.perform(get("/character/" + other.getId() + "/skills"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(skills).contains("name=\"luck\"").contains("Удача: есть 6");

        mockMvc.perform(post("/campaign/" + campaign.getId() + "/session"));
        flush();
        assertThat(luck()).isEqualTo(6);
    }

    private Long participantOf(Character character) {
        return encounterRepository.findByCampaignId(campaign.getId()).orElseThrow().getParticipants().stream()
                .filter(participant -> participant.getCharacter().getId().equals(character.getId()))
                .findFirst().orElseThrow().getId();
    }

    @Test
    @DisplayName("Спасбросок в трекере: удача тратится и прибавляется к порогу (HOMEBREW)")
    void save() {
        encounterService.start(campaign.getId(), List.of(lucky.getId(), other.getId()));
        flush();
        when(dice.roll(anyInt())).thenReturn(9);

        var event = encounterService.save(campaign.getId(), participantOf(lucky), "STUN", 3);
        flush();

        assertThat(event.getSteps()).anyMatch(step -> step.contains("Удача +3") && step.contains("нужно ≤ 9") && step.contains("успех"));
        assertThat(luck()).isEqualTo(3);
    }

    @Test
    @DisplayName("Facedown: удача начавшего прибавляется к его броску")
    void facedown() {
        encounterService.start(campaign.getId(), List.of(lucky.getId(), other.getId()));
        flush();

        var result = encounterService.contest(campaign.getId(), participantOf(lucky), "FACEDOWN", other.getId(), 2);
        flush();

        assertThat(result.initiator().result().steps()).anyMatch(step -> step.contains("Удача +2"));
        assertThat(luck()).isEqualTo(4);
    }
}
