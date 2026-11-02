package app.charka.system.cp2020;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

/** TECH 6 у всех по умолчанию */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Лечение по книге CP2020 (с. 115–116)")
class Cp2020MedicineTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private HealthCounterService healthCounterService;
    @Autowired private SkillService skillService;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character patient;
    private Character medic;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        patient = member("Раненый");
        medic = member("Медик");
        skillService.setRoles(medic.getId(), List.of("medtechie"));
        skillService.updateLevels(medic.getId(), Map.of("medical_tech", 5), Map.of());
        health(patient).setValue(12);
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

    private Counter health(Character character) {
        return characterRepository.findById(character.getId()).orElseThrow().getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Медик: TECH + Медицинская техника + 1D10 больше урона — лечение 1 в день; промотка — в отчёт")
    void treatThenHeal() throws Exception {
        // 6 + 5 + 3 = 14 > 12
        when(dice.roll(anyInt())).thenReturn(3);
        healthCounterService.care(patient.getId(), health(patient).getId(), medic.getId(), "TREAT_MED", 0);
        flush();
        assertThat(health(patient).getAttribute(Cp2020HealthStrategy.CARE, 0)).isEqualTo(2);

        mockMvc.perform(post("/campaign/" + campaign.getId() + "/chronicles").param("name", "Неделя").param("duration", "4"))
                .andExpect(flash().attribute("timeReport", List.of("Раненый: Медицинская техника: 1 в день, за 4 дн. урон 12 → 8")));
        flush();
        assertThat(health(patient).getValue()).isEqualTo(8);
    }

    @Test
    @DisplayName("Провал броска — ничего; Медицинскую технику себе нельзя")
    void failAndSelf() {
        // 6 + 5 + 1 = 12, нужно больше 12
        when(dice.roll(anyInt())).thenReturn(1);
        assertThat(healthCounterService.care(patient.getId(), health(patient).getId(), medic.getId(), "TREAT_MED", 0).getSummary())
                .contains("не удалось");
        assertThatThrownBy(() -> healthCounterService.care(patient.getId(), health(patient).getId(), patient.getId(), "TREAT_MED", 0))
                .hasMessageContaining("Себе");
    }

    @Test
    @DisplayName("Карточка: строка «без лечения» и форма медика, медик с навыком — первым")
    void card() throws Exception {
        String page = mockMvc.perform(get("/character/" + patient.getId() + "/counters"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("без лечения: +2 урона в день").contains("/health/care").contains("Лечить: Медицинская техника");
        assertThat(page.indexOf(">Медик</option>")).isLessThan(page.indexOf(">Раненый</option>"));
    }

    @Test
    @DisplayName("Журнал здоровья (G-09): прошедший урон и успешная помощь пишутся сами; лист — состояние в шапке")
    void journal() throws Exception {
        healthCounterService.applyDamage(patient.getId(), app.charka.model.counters.strategy.health.DamageRequest.builder()
                .amount(7).location("TORSO").damageType("NORMAL").autoSave(false).build());
        // урон теперь 17: 6 + 5 + 3 + больница 5 = 19 > 17
        when(dice.roll(anyInt())).thenReturn(3);
        healthCounterService.care(patient.getId(), health(patient).getId(), medic.getId(), "TREAT_MED", 5);
        flush();

        List<app.charka.model.Wound> log = characterRepository.findById(patient.getId()).orElseThrow().getWounds();
        assertThat(log).hasSize(2);
        assertThat(log.get(0).getSeverity()).isEqualTo(5L);
        assertThat(log.get(0).getDate()).isEqualTo(LocalDate.of(2037, 1, 1));
        assertThat(log.get(1).getName()).startsWith("Медик: ");

        String sheet = mockMvc.perform(get("/character/" + patient.getId()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(sheet).contains("Журнал здоровья").contains("Смертельное").contains("Урон 5");
    }
}
