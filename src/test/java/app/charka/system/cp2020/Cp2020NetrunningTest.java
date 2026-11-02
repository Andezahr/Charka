package app.charka.system.cp2020;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.system.GameSystemRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Нетраннинг CP2020: дека и программы (P-05)")
class Cp2020NetrunningTest {

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private GameSystemRegistry registry;
    @Autowired private EntityManager entityManager;
    @Autowired private app.charka.repository.CharacterRepository characterRepository;
    @Autowired private app.charka.service.MoneyService moneyService;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Character runner;
    private Long inventoryId;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        Campaign campaign = campaignRepository.save(created);
        Character character = new Character();
        character.setName("Спайдер");
        runner = characterService.createInCampaign(campaign.getId(), character);
        inventoryId = runner.getInventories().getFirst().getId();
        flush();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private Item add(String key) {
        Item item = itemService.addFromCatalog(inventoryId, key, 1);
        flush();
        return item;
    }

    private String act(Item item, String action) {
        String result = itemService.performAction(runner.getId(), item.getId(), action, Map.of());
        flush();
        return result;
    }

    @Test
    @DisplayName("Программа грузится в деку, пока хватает памяти: 10 MU — Pit Bull 6 и Worm 5 не влезают вместе")
    void memory() {
        Item deck = add("deck-standard");
        Item pitBull = add("program-pit-bull");
        Item worm = add("program-worm");

        assertThat(act(pitBull, Cp2020Netrunning.LOAD)).contains("MU 6/10");
        assertThatThrownBy(() -> act(worm, Cp2020Netrunning.LOAD)).hasMessageContaining("5 MU");

        Item reloaded = itemService.findById(deck.getId());
        assertThat(registry.get("CP2020").getItemSummary(reloaded).main()).isEqualTo("MU 6/10");

        act(pitBull, Cp2020Netrunning.UNLOAD);
        assertThat(act(worm, Cp2020Netrunning.LOAD)).contains("MU 5/10");
    }

    @Test
    @DisplayName("Утилита срабатывает на 1D10 ≤ силы; антиперсональная — сила + INT + Interface + 1D10")
    void run() {
        add("deck-standard");
        Item genie = add("program-genie");
        Item hellbolt = add("program-hellbolt");
        act(genie, Cp2020Netrunning.LOAD);
        act(hellbolt, Cp2020Netrunning.LOAD);
        when(dice.roll(anyInt())).thenReturn(5);

        assertThat(act(genie, Cp2020Netrunning.RUN)).contains("1D10 = 5 ≤ силы 5").contains("сработала");
        // сила 4 + INT 6 + Interface 0 + 5
        assertThat(act(hellbolt, Cp2020Netrunning.RUN)).contains("Hellbolt: 15");
    }

    @Test
    @DisplayName("Улучшение деки за деньги (с. 134): скорость +1 за 2000 eb; денег мало — отказ; память до 20 MU")
    void upgrade() {
        Item deck = add("deck-standard");
        assertThatThrownBy(() -> act(deck, Cp2020Netrunning.UPGRADE_SPEED)).hasMessageContaining("2000");

        app.charka.model.Money income = new app.charka.model.Money();
        income.setName("Гонорар");
        income.setAmount(7000L);
        moneyService.create(runner.getId(), income);
        flush();

        assertThat(act(deck, Cp2020Netrunning.UPGRADE_SPEED)).contains("0 → 1").contains("2000");
        assertThat(act(deck, Cp2020Netrunning.UPGRADE_MEMORY)).contains("10 → 20");
        assertThat(registry.get("CP2020").getItemSummary(itemService.findById(deck.getId())).main()).isEqualTo("MU 0/20");
        assertThatThrownBy(() -> act(deck, Cp2020Netrunning.UPGRADE_MEMORY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Сетевой бой (с. 151): Hellbolt против нетраннера без защиты — попадание, 1D10 урона в здоровье; инициатива в Сети")
    void hellbolt() {
        Character victim = new Character();
        victim.setName("Жертва");
        victim = characterService.createInCampaign(runner.getCampaign().getId(), victim);
        Item deck = add("deck-standard");
        Item hellbolt = add("program-hellbolt");
        act(hellbolt, Cp2020Netrunning.LOAD);
        when(dice.roll(anyInt())).thenReturn(5);

        assertThat(act(deck, Cp2020Netrunning.NET_INITIATIVE)).contains("= 11");
        String result = itemService.performAction(runner.getId(), hellbolt.getId(), Cp2020Netrunning.RUN,
                Map.of("source", String.valueOf(victim.getId())));
        flush();

        // атака 4 + INT 6 + 0 + 5 = 15 против 0 + 6 + 0 + 5 = 11
        assertThat(result).contains("Попадание").contains("1D10 = 5 урона");
        assertThat(registry.get("CP2020").getHealthStatus(characterRepository.findById(victim.getId()).orElseThrow())).isNotNull();
        assertThat(characterRepository.findById(victim.getId()).orElseThrow().getCounters())
                .filteredOn(c -> Cp2020HealthStrategy.TYPE.equals(c.getType()))
                .singleElement().satisfies(health -> assertThat(health.getValue()).isPositive());
    }

    @Test
    @DisplayName("Защитная программа цели идёт в защиту: Reflector 5 отбивает")
    void shielded() {
        Character victim = new Character();
        victim.setName("Защищённый");
        victim = characterService.createInCampaign(runner.getCampaign().getId(), victim);
        Long victimInventory = victim.getInventories().getFirst().getId();
        add("deck-standard");
        Item sword = add("program-sword");
        act(sword, Cp2020Netrunning.LOAD);
        itemService.addFromCatalog(victimInventory, "deck-standard", 1);
        Item reflector = itemService.addFromCatalog(victimInventory, "program-reflector", 1);
        flush();
        itemService.performAction(victim.getId(), reflector.getId(), Cp2020Netrunning.LOAD, Map.of());
        flush();
        when(dice.roll(anyInt())).thenReturn(5);

        String result = itemService.performAction(runner.getId(), sword.getId(), Cp2020Netrunning.RUN,
                Map.of("source", String.valueOf(victim.getId())));

        // 3 + 6 + 0 + 5 = 14 против 5 + 6 + 0 + 5 = 16
        assertThat(result).contains("Reflector").contains("Отбито");
    }

    @Test
    @DisplayName("Незагруженную программу не запустить: на строке только «В деку»")
    void notLoaded() {
        Item hammer = add("program-hammer");

        assertThat(registry.get("CP2020").getItemActions(itemService.findById(hammer.getId())))
                .singleElement().satisfies(action -> {
                    assertThat(action.code()).isEqualTo(Cp2020Netrunning.LOAD);
                    assertThat(action.unavailable()).contains("Нет деки");
                });
    }
}
