package app.charka.undo;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.Money;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.UndoEntryRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.service.campaign.CampaignExportService;
import app.charka.system.cp2020.Cp2020HealthStrategy;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
@DisplayName("Отмена последнего действия (A-01)")
class UndoTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private CampaignExportService exportService;
    @Autowired private UndoEntryRepository undoRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character shooter;
    private Character victim;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        shooter = member("Колясик");
        victim = member("Олежа");
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

    /** Экспорт кампании деревом: порядок ключей в словарях (атрибуты счётчика) не важен */
    private com.fasterxml.jackson.databind.JsonNode export() throws Exception {
        clear();
        return objectMapper.readTree(exportService.exportCampaignToJson(campaignRepository.findById(campaign.getId()).orElseThrow()));
    }

    private Long inventory(Character character) {
        return characterRepository.findById(character.getId()).orElseThrow().getInventories().get(0).getId();
    }

    private Counter health(Character character) {
        return characterRepository.findById(character.getId()).orElseThrow().getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType())).findFirst().orElseThrow();
    }

    private UndoService.Offer offer(MvcResult result) {
        Object offer = result.getFlashMap().get(UndoInterceptor.OFFER);
        assertThat(offer).as("после действия предложено отменить").isInstanceOf(UndoService.Offer.class);
        return (UndoService.Offer) offer;
    }

    private MvcResult undo(UndoService.Offer offer) throws Exception {
        return mockMvc.perform(post("/campaign/" + campaign.getId() + "/undo/" + offer.id())
                        .header("Referer", "http://localhost/character/" + shooter.getId()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    private MvcResult damage(Character character, int amount) throws Exception {
        return mockMvc.perform(post("/character/" + character.getId() + "/counters/" + health(character).getId() + "/health/damage")
                        .param("amount", String.valueOf(amount)).param("location", "TORSO"))
                .andExpect(status().is3xxRedirection()).andReturn();
    }

    @Nested
    @DisplayName("Отменяется одним нажатием, состояние как до действия")
    class Restores {

        @Test
        @DisplayName("Урон по ошибке: здоровье, броня и журнал здоровья — как были; подпись с именем")
        void damageByMistake() throws Exception {
            var before = export();

            UndoService.Offer offer = offer(damage(victim, 12));
            assertThat(offer.label()).isEqualTo("Олежа: урон");
            assertThat(export()).isNotEqualTo(before);

            MvcResult undone = undo(offer);

            assertThat(undone.getResponse().getRedirectedUrl()).isEqualTo("/character/" + shooter.getId());
            assertThat(undone.getFlashMap().get("undoDone")).isEqualTo("Отменено: Олежа: урон");
            assertThat(export()).isEqualTo(before);
            assertThat(health(victim).getValue()).isZero();
        }

        @Test
        @DisplayName("Очередь: патроны, урон и износ брони цели возвращаются")
        void burst() throws Exception {
            Item minami = itemService.addFromCatalog(inventory(shooter), "arasaka_minami_10", 1);
            Item jacket = itemService.addFromCatalog(inventory(victim), "armor_light_jacket", 1);
            itemService.performAction(victim.getId(), jacket.getId(), "WEAR", java.util.Map.of());
            var before = export();
            when(dice.roll(anyInt())).thenReturn(10, 6, 6);

            MvcResult attack = mockMvc.perform(post("/character/" + shooter.getId() + "/items/" + minami.getId() + "/attack")
                            .param("target", String.valueOf(victim.getId())).param("distance", "5").param("mode", "BURST"))
                    .andExpect(status().is3xxRedirection()).andReturn();
            UndoService.Offer offer = offer(attack);
            assertThat(offer.label()).startsWith("Колясик: атака — ");
            assertThat(export()).isNotEqualTo(before);

            undo(offer);

            assertThat(export()).isEqualTo(before);
        }

        @Test
        @DisplayName("Покупка: предмет исчезает, запись в деньгах тоже")
        void purchase() throws Exception {
            Character buyer = characterRepository.findById(shooter.getId()).orElseThrow();
            Money money = new Money();
            money.setName("Аванс");
            money.setAmount(5000L);
            money.setOperationDate(LocalDate.of(2037, 1, 1));
            money.setCharacter(buyer);
            buyer.getMoneyList().add(money);
            characterRepository.save(buyer);
            var before = export();

            MvcResult bought = mockMvc.perform(post("/character/" + shooter.getId() + "/inventories/" + inventory(shooter) + "/items/catalog")
                            .param("catalogKey", "arasaka_minami_10").param("quantity", "1").param("buy", "true"))
                    .andExpect(status().is3xxRedirection()).andReturn();
            UndoService.Offer offer = offer(bought);
            assertThat(offer.label()).isEqualTo("Колясик: покупка");

            undo(offer);

            assertThat(export()).isEqualTo(before);
        }

        @Test
        @DisplayName("Промотка на 30 дней: дата, хроника и лечение — назад")
        void thirtyDays() throws Exception {
            damage(victim, 6);
            undoRepository.deleteAll();
            var before = export();

            MvcResult passed = mockMvc.perform(post("/campaign/" + campaign.getId() + "/chronicles")
                            .param("name", "Отпуск").param("duration", "30"))
                    .andExpect(status().is3xxRedirection()).andReturn();
            UndoService.Offer offer = offer(passed);
            assertThat(offer.label()).isEqualTo("Промотка времени");
            assertThat(campaignRepository.findById(campaign.getId()).orElseThrow().getCurrentDate()).isEqualTo(LocalDate.of(2037, 1, 31));

            undo(offer);

            assertThat(export()).isEqualTo(before);
            assertThat(campaignRepository.findById(campaign.getId()).orElseThrow().getCurrentDate()).isEqualTo(LocalDate.of(2037, 1, 1));
        }

        @Test
        @DisplayName("Два действия подряд отменяются по одному, с последнего")
        void stack() throws Exception {
            var before = export();
            UndoService.Offer first = offer(damage(victim, 3));
            UndoService.Offer second = offer(damage(shooter, 4));

            MvcResult refused = undo(first);
            assertThat((String) refused.getFlashMap().get("undoError")).contains("только последнее");

            undo(second);
            undo(first);
            assertThat(export()).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("Что не становится отменой")
    class NotRecorded {

        @Test
        @DisplayName("Ничего не изменилось (бросок, отказ) — кнопки нет")
        void nothingChanged() throws Exception {
            MvcResult roll = mockMvc.perform(post("/character/" + shooter.getId() + "/roll").param("expression", "1d10"))
                    .andExpect(status().is3xxRedirection()).andReturn();
            MvcResult broke = mockMvc.perform(post("/character/" + shooter.getId() + "/items/999999/attack")
                    .param("distance", "5")).andReturn();

            assertThat((java.util.Map<String, Object>) roll.getFlashMap()).doesNotContainKey(UndoInterceptor.OFFER);
            assertThat((java.util.Map<String, Object>) broke.getFlashMap()).doesNotContainKey(UndoInterceptor.OFFER);
            assertThat(undoRepository.findByCampaignIdOrderByIdDesc(campaign.getId())).isEmpty();
        }

        @Test
        @DisplayName("Хранятся последние 20 действий")
        void keepsTwenty() throws Exception {
            for (int index = 0; index < UndoService.KEEP + 3; index++) {
                damage(victim, 1);
            }
            assertThat(undoRepository.findByCampaignIdOrderByIdDesc(campaign.getId())).hasSize(UndoService.KEEP);
        }
    }

    @Nested
    @DisplayName("На странице")
    class Page {

        @Test
        @DisplayName("Плашка «↶ Отменить» после действия; в ленте чата — действие и отметка об отмене")
        void toastAndChat() throws Exception {
            MvcResult damaged = damage(victim, 5);

            String sheet = mockMvc.perform(get("/character/" + victim.getId()).flashAttrs(damaged.getFlashMap()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(sheet).contains("undo-toast", "Олежа: урон", "↶ Отменить");

            String feed = mockMvc.perform(get("/campaign/" + campaign.getId() + "/chat"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(feed).contains("✓ урон", "/undo/" + offer(damaged).id());

            undo(offer(damaged));
            String after = mockMvc.perform(get("/campaign/" + campaign.getId() + "/chat"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(after).contains("↶ Отменено: Олежа: урон").doesNotContain("/undo/");
        }
    }
}
