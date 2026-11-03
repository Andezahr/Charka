package app.charka.system.cp2020;

import app.charka.effect.EffectTemplate.DurationUnit;
import app.charka.effect.TimedEffectService;
import app.charka.effect.TimedEffects;
import app.charka.encounter.EncounterService;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.TimedEffect;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.service.campaign.CampaignTimelineService;
import app.charka.system.GameSystemRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

/** Кубики всегда 3: срок 1D6+1 = 4 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@DisplayName("Временные эффекты и препараты (P-03, с. 122–123)")
class Cp2020DrugsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private TimedEffectService effectService;
    @Autowired private CampaignTimelineService timelineService;
    @Autowired private EncounterService encounterService;
    @Autowired private GameSystemRegistry registry;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character user;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        Character character = new Character();
        character.setName("Торчок");
        user = characterService.createInCampaign(campaign.getId(), character);
        flush();
        when(dice.roll(anyInt())).thenReturn(3);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private Character reload() {
        return characterRepository.findById(user.getId()).orElseThrow();
    }

    private int stat(String code) {
        Character character = reload();
        return registry.get("CP2020").getCurrentStats(character).get(code).value();
    }

    @Nested
    @DisplayName("Препарат из инвентаря")
    class TakeDrug {

        @Test
        @DisplayName("Boost: доза списывается, INT +1 на 4 часа, через день эффект снят")
        void boost() {
            Long inventoryId = reload().getInventories().getFirst().getId();
            Item boost = itemService.addFromCatalog(inventoryId, "drug-boost", 2);
            flush();
            int before = stat(Cp2020StatsStrategy.INT);

            String result = itemService.performAction(user.getId(), boost.getId(), Cp2020Drugs.USE, Map.of());
            flush();

            assertThat(result).contains("Boost").contains("4 ч").contains("осталось 1");
            assertThat(stat(Cp2020StatsStrategy.INT)).isEqualTo(before + 1);
            assertThat(reload().getEffects()).singleElement()
                    .satisfies(effect -> assertThat(effect.getUnit()).isEqualTo(TimedEffect.Unit.HOUR));

            List<String> report = timelineService.proceedDate(campaign.getId(), 1, "Отходняк");
            flush();

            assertThat(report).anyMatch(line -> line.contains("Boost"));
            assertThat(reload().getEffects()).isEmpty();
            assertThat(stat(Cp2020StatsStrategy.INT)).isEqualTo(before);
        }

        @Test
        @DisplayName("Speedheal включает ускоренное лечение и вешает отходняк −1 REF на 7 дней")
        void speedheal() {
            Long inventoryId = reload().getInventories().getFirst().getId();
            Item speedheal = itemService.addFromCatalog(inventoryId, "drug-speedheal", 1);
            flush();

            String result = itemService.performAction(user.getId(), speedheal.getId(), Cp2020Drugs.USE, Map.of());
            flush();

            assertThat(result).contains("лечение ускорено").contains("REF −1");
            Character character = reload();
            assertThat(character.getCounters()).filteredOn(c -> Cp2020HealthStrategy.TYPE.equals(c.getType()))
                    .singleElement().satisfies(health -> assertThat(health.getFlag(Cp2020HealthStrategy.SPEEDHEAL)).isTrue());
            assertThat(character.getEffects()).anySatisfy(effect -> {
                assertThat(effect.getUnit()).isEqualTo(TimedEffect.Unit.DAY);
                assertThat(effect.getModifier()).isEqualTo(-1);
            });
        }

        @Test
        @DisplayName("Последняя доза убирает предмет")
        void lastDose() {
            Long inventoryId = reload().getInventories().getFirst().getId();
            Item stim = itemService.addFromCatalog(inventoryId, "drug-stim", 1);
            flush();

            String result = itemService.performAction(user.getId(), stim.getId(), Cp2020Drugs.USE, Map.of());
            flush();

            assertThat(result).contains("последняя");
            assertThat(reload().getInventories().getFirst().getContent()).noneMatch(item -> Cp2020Drugs.isDrug(item));
        }
    }

    @Nested
    @DisplayName("Ход времени")
    class Time {

        @Test
        @DisplayName("Минуты идут раундами в бою: 'Dorph на 4 раунда кончается на четвёртом новом раунде")
        void rounds() {
            effectService.applyTemplate(user.getId(), "DORPH");
            Character other = new Character();
            other.setName("Враг");
            other = characterService.createInCampaign(campaign.getId(), other);
            encounterService.start(campaign.getId(), List.of(user.getId(), other.getId()));
            flush();

            advanceRounds(3);
            assertThat(reload().getEffects()).hasSize(1).first().satisfies(effect -> assertThat(effect.getRemaining()).isEqualTo(1));

            advanceRounds(1);
            assertThat(reload().getEffects()).isEmpty();
        }

        private void advanceRounds(int rounds) {
            int target = encounterService.activeRound(campaign.getId()) + rounds;
            for (int guard = 0; encounterService.activeRound(campaign.getId()) < target && guard < 20; guard++) {
                encounterService.next(campaign.getId());
                flush();
            }
        }

        @Test
        @DisplayName("Минуты хранятся раундами: 4 минуты — 80 раундов, «≈4 мин»")
        void minutes() {
            Character character = reload();
            TimedEffect effect = TimedEffects.add(character, "Stim", null, 0, "4", DurationUnit.MINUTE, null, dice);

            assertThat(effect.getRemaining()).isEqualTo(80);
            assertThat(effect.getRemainingLabel()).isEqualTo("≈4 мин");
        }

        @Test
        @DisplayName("Дни убывают при промотке")
        void days() {
            effectService.create(user.getId(), "Отходняк от Speedheal", Cp2020StatsStrategy.REF, -1, "7", DurationUnit.DAY, null);
            flush();
            timelineService.proceedDate(campaign.getId(), 3, "Три дня");
            flush();

            assertThat(reload().getEffects()).singleElement().satisfies(effect -> assertThat(effect.getRemaining()).isEqualTo(4));
        }
    }

    @Nested
    @DisplayName("Лист")
    class Sheet {

        @Test
        @DisplayName("Блок эффектов: готовый препарат одним нажатием, снять раньше срока")
        void page() throws Exception {
            mockMvc.perform(post("/character/" + user.getId() + "/effects").param("template", "BLACK_LACE"));
            flush();
            String page = mockMvc.perform(get("/character/" + user.getId()))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(page).contains("id=\"effects\"").contains("Black Lace").contains("+2 COOL");

            Long effectId = reload().getEffects().getFirst().getId();
            mockMvc.perform(post("/character/" + user.getId() + "/effects/" + effectId + "/delete"));
            flush();
            assertThat(reload().getEffects()).isEmpty();
        }

        @Test
        @DisplayName("Свой эффект: неизвестный стат — отказ")
        void unknownStat() {
            assertThatThrownBy(() -> effectService.create(user.getId(), "Странное", "mana", 1, "3", DurationUnit.ROUND, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
