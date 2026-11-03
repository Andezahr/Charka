package app.charka.encounter;

import app.charka.attack.AttackForm;
import app.charka.attack.AttackRequest;
import app.charka.attack.AttackResult;
import app.charka.attack.AttackService;
import app.charka.attack.AttackSource;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CombatEffect;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.system.GameSystemRegistry;
import app.charka.system.cp2020.Cp2020HealthStrategy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Эффекты по раундам боя")
class EncounterEffectsTest {

    @Autowired private EncounterService encounterService;
    @Autowired private AttackService attackService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private GameSystemRegistry registry;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character thrower;
    private Character victim;

    @BeforeEach
    void setUp() {
        Campaign created = new Campaign();
        created.setName("Тест");
        created.setSystem("CP2020");
        created.setStartDate(LocalDate.of(2037, 1, 1));
        created.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(created);
        thrower = member("Метатель");
        victim = member("Цель");
        when(dice.roll(anyInt())).thenReturn(5);
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

    private void fight() {
        encounterService.start(campaign.getId(), List.of(thrower.getId(), victim.getId()));
        flush();
    }

    private Item item(String catalogKey) {
        Long inventory = characterRepository.findById(thrower.getId()).orElseThrow().getInventories().get(0).getId();
        return itemService.addFromCatalog(inventory, catalogKey, 1);
    }

    /** Сначала бросается урон, потом попадание: всё по единице, кроме него, поэтому цель выживает все раунды */
    private AttackResult throwAtVictim(String catalogKey) {
        Item grenade = item(catalogKey);
        if (catalogKey.equals("grenade_incendiary")) {
            when(dice.roll(anyInt())).thenReturn(1, 1, 1, 1, 9, 1);
        } else {
            when(dice.roll(anyInt())).thenReturn(9, 1);
        }
        AttackResult result = attackService.attack(thrower.getId(), grenade.getId(),
                new AttackRequest(List.of(victim.getId()), null, 1, null, null, Set.of(), 0, "THROW", 0, 0));
        flush();
        return result;
    }

    private EncounterView.Row row() {
        return encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                .filter(row -> row.character().getId().equals(victim.getId()))
                .findFirst().orElseThrow();
    }

    private int damage() {
        return characterRepository.findById(victim.getId()).orElseThrow().getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType()))
                .findFirst().orElseThrow().getValue();
    }

    /** Все ходят, начинается следующий раунд */
    private List<String> nextRound() {
        int round = encounterService.view(campaign.getId()).orElseThrow().encounter().getRound();
        List<String> report = new ArrayList<>();
        while (encounterService.view(campaign.getId()).orElseThrow().encounter().getRound() == round) {
            report.addAll(encounterService.next(campaign.getId()).report());
            flush();
        }
        return report;
    }

    @Test
    @DisplayName("Зажигательная горит ещё два раунда, потом снимается сама")
    void incendiaryBurns() {
        fight();
        throwAtVictim("grenade_incendiary");
        int afterBlast = damage();

        assertThat(afterBlast).isPositive();
        assertThat(row().effects()).extracting(CombatEffect::getSummary).containsExactly("Горит 4D6 · раундов 2");

        List<String> second = nextRound();
        assertThat(damage()).isGreaterThan(afterBlast);
        assertThat(second).anyMatch(line -> line.startsWith("Цель, горит:"));
        assertThat(row().effects()).extracting(CombatEffect::getSummary).containsExactly("Горит 4D6 · раундов 1");

        int afterSecond = damage();
        List<String> third = nextRound();
        assertThat(damage()).isGreaterThan(afterSecond);
        assertThat(third).anyMatch(line -> line.contains("закончилось"));
        assertThat(row().effects()).isEmpty();

        int afterThird = damage();
        assertThat(nextRound()).isEmpty();
        assertThat(damage()).isEqualTo(afterThird);
    }

    @Test
    @DisplayName("Снова подожгли: эффект заменяется, а не складывается")
    void noStacking() {
        fight();
        throwAtVictim("grenade_incendiary");
        nextRound();
        throwAtVictim("grenade_incendiary");

        assertThat(row().effects()).extracting(CombatEffect::getRoundsLeft).containsExactly(2);
    }

    @Test
    @DisplayName("Потушили: одно нажатие снимает эффект")
    void removed() {
        fight();
        throwAtVictim("grenade_incendiary");

        encounterService.removeEffect(campaign.getId(), row().participant().getId(), row().effects().get(0).getId());
        flush();

        assertThat(row().effects()).isEmpty();
        int damage = damage();
        assertThat(nextRound()).isEmpty();
        assertThat(damage()).isEqualTo(damage);
    }

    @Test
    @DisplayName("Ослепляющая: без урона 4 раунда, в диалоге атаки сразу отмечено −3")
    void dazzled() {
        fight();
        AttackResult result = throwAtVictim("grenade_dazzle");

        assertThat(result.hit()).isTrue();
        assertThat(damage()).isZero();
        assertThat(row().attackOptions()).containsExactly("blinded");

        nextRound();
        nextRound();
        nextRound();
        assertThat(row().effects()).extracting(CombatEffect::getRoundsLeft).containsExactly(1);
        assertThat(nextRound()).anyMatch(line -> line.contains("закончилось"));
        assertThat(row().attackOptions()).isEmpty();
        assertThat(damage()).isZero();
    }

    @Test
    @DisplayName("Оглушающая: спасбросок с −5, провал оглушает без урона")
    void stunned() {
        fight();
        Item grenade = item("grenade_stun");
        when(dice.roll(anyInt())).thenReturn(9);

        AttackResult result = attackService.attack(thrower.getId(), grenade.getId(),
                new AttackRequest(List.of(victim.getId()), null, 1, null, null, Set.of(), 0, "THROW", 0, 0));
        flush();

        assertThat(result.steps()).anyMatch(step -> step.contains("с -5"));
        assertThat(damage()).isZero();
        assertThat(row().card().saves()).extracting(CombatCard.Save::code).containsExactly("STUN");
    }

    @Test
    @DisplayName("Умершему догорание не бросается, бой продолжается")
    void deadStopsBurning() {
        fight();
        throwAtVictim("grenade_incendiary");
        characterRepository.findById(victim.getId()).orElseThrow().getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType()))
                .findFirst().orElseThrow().setFlag(Cp2020HealthStrategy.DEAD, true);
        flush();

        assertThat(nextRound()).isEmpty();
        assertThat(row().effects()).isEmpty();
    }

    @Test
    @DisplayName("Вне боя эффект ведёт мастер: об этом сказано в расчёте")
    void outsideFight() {
        AttackResult result = throwAtVictim("grenade_incendiary");

        assertThat(result.steps()).anyMatch(step -> step.contains("вне боя ведёт мастер"));
    }

    @Test
    @DisplayName("Ослепляющая упала мимо: заряд без урона ждёт мастера и применяется как эффект")
    void pendingEffect() {
        fight();
        Item grenade = item("grenade_dazzle");
        when(dice.roll(anyInt())).thenReturn(1);

        AttackResult result = attackService.attack(thrower.getId(), grenade.getId(),
                new AttackRequest(List.of(victim.getId()), null, 1, null, null, Set.of(), 0, "THROW", 0, 0));
        flush();

        assertThat(result.pendingBlast()).isNotNull();
        assertThat(attackService.pendingBlasts(thrower.getId()).get(0).getAmount()).isNull();

        attackService.applyBlast(thrower.getId(), result.pendingBlast(), List.of(victim.getId()));
        flush();

        assertThat(row().attackOptions()).containsExactly("blinded");
    }

    @Test
    @DisplayName("Гранатомёт: зарядили гранатой, бьёт её уроном и зоной на 225 м, заряд ушёл")
    void launcher() {
        Item launcher = item("grenade_launcher");
        Item grenade = item("grenade_incendiary");
        flush();

        assertThat(attackForm(launcher).unavailable()).contains("не заряжено");

        itemService.performAction(thrower.getId(), launcher.getId(), "RELOAD", Map.of("source", String.valueOf(grenade.getId())));
        flush();
        assertThat(attackForm(launcher).unavailable()).isNull();
        assertThat(attackForm(launcher).range()).isEqualTo(225);
        assertThat(thrower().getInventories().get(0).getContent()).noneMatch(item -> item.getName().equals("Grenade: Incendiary"));

        fight();
        when(dice.roll(anyInt())).thenReturn(1, 1, 1, 1, 9, 1);
        AttackResult result = attackService.attack(thrower.getId(), launcher.getId(),
                new AttackRequest(List.of(victim.getId()), null, 1, null, null, Set.of(), 0, "SINGLE", 0, 0));
        flush();

        assertThat(result.outcome()).contains("Зона 5 м");
        assertThat(damage()).isPositive();
        assertThat(row().effects()).extracting(CombatEffect::getLabel).containsExactly("Горит");
        assertThat(attackForm(launcher).unavailable()).contains("не заряжено");
    }

    @Test
    @DisplayName("Разрядить гранатомёт: граната возвращается в инвентарь и снова метается")
    void launcherUnload() {
        Item launcher = item("grenade_launcher");
        Item grenade = item("grenade_fragmentation");
        flush();
        itemService.performAction(thrower.getId(), launcher.getId(), "RELOAD", Map.of("source", String.valueOf(grenade.getId())));
        flush();

        itemService.performAction(thrower.getId(), launcher.getId(), "UNLOAD", Map.of());
        flush();

        assertThat(thrower().getInventories().get(0).getContent())
                .filteredOn(item -> item.getName().equals("Grenade: Fragmentation"))
                .singleElement().satisfies(item -> {
                    assertThat(item.getQuantity()).isEqualTo(1);
                    assertThat(item.getProperties()).containsEntry("damage", "7D6").containsEntry("area", "5").containsKey("thrown");
                });
        assertThat(attackForm(launcher).unavailable()).contains("не заряжено");
    }

    @Test
    @DisplayName("Нервный газ: спасбросок в раунд взрыва и ещё раз в следующем, потом рассеялся")
    void nerveGas() {
        fight();
        Item grenade = item("grenade_gas_nerve");
        // 8D10 по единице, попадание 9, спасбросок 1 — успех
        when(dice.roll(anyInt())).thenReturn(1, 1, 1, 1, 1, 1, 1, 1, 9, 1);

        AttackResult result = attackService.attack(thrower.getId(), grenade.getId(),
                new AttackRequest(List.of(victim.getId()), null, 1, null, null, Set.of("cover_brick"), 0, "THROW", 0, 0));
        flush();

        assertThat(result.outcome()).contains("Зона 3 м");
        assertThat(result.steps()).anyMatch(step -> step.contains("Спасбросок от яда")).noneMatch(step -> step.contains("Укрытие"));
        int afterBlast = damage();
        assertThat(afterBlast).isPositive();
        assertThat(row().effects()).extracting(CombatEffect::getLabel).containsExactly("В облаке газа");

        List<String> next = nextRound();

        assertThat(damage()).isGreaterThan(afterBlast);
        assertThat(next).anyMatch(line -> line.contains("в облаке газа")).anyMatch(line -> line.contains("закончилось"));
        assertThat(row().effects()).isEmpty();
    }

    @Test
    @DisplayName("Укрытие выбирается в атаке: кирпич останавливает пулю")
    void coverStopsBullet() {
        Item minami = item("arasaka_minami_10");
        when(dice.roll(anyInt())).thenReturn(10, 1, 3, 1, 1);

        AttackResult result = attackService.attack(thrower.getId(), minami.getId(),
                new AttackRequest(List.of(victim.getId()), null, 1, null, null, Set.of("cover_brick"), 0, "SINGLE", 0, 0));
        flush();

        assertThat(result.hit()).isTrue();
        assertThat(result.outcome()).contains("укрытие и броня выдержали");
        assertThat(damage()).isZero();
    }

    private Character thrower() {
        return characterRepository.findById(thrower.getId()).orElseThrow();
    }

    private AttackForm attackForm(Item item) {
        Item loaded = thrower().getInventories().get(0).getContent().stream()
                .filter(candidate -> candidate.getId().equals(item.getId())).findFirst().orElseThrow();
        return registry.forCharacter(thrower()).getAttackRules().form(AttackSource.of(loaded), registry.forCharacter(thrower()).getCurrentStats(thrower()));
    }

    @Test
    @DisplayName("РПГ стреляет один раз, заряд приходит из запаса")
    void rpgReloads() {
        Item rpg = item("militech_arms_rpg_a");
        Item round = item("ammo_rpg");
        when(dice.roll(anyInt())).thenReturn(1, 1, 1, 1, 1, 1, 9, 5);

        AttackResult result = attackService.attack(thrower.getId(), rpg.getId(),
                new AttackRequest(List.of(victim.getId()), null, 1, null, null, Set.of(), 0, "SINGLE", 0, 0));
        flush();

        assertThat(result.outcome()).contains("Зона 4 м");
        assertThat(damage()).isPositive();

        itemService.performAction(thrower.getId(), rpg.getId(), "RELOAD", Map.of("source", String.valueOf(round.getId())));
        flush();

        assertThat(characterRepository.findById(thrower.getId()).orElseThrow().getInventories().get(0).getContent())
                .filteredOn(item -> item.getName().equals("Militech Arms RPG-A"))
                .singleElement().satisfies(item -> assertThat(item.getProperties()).containsEntry("ammo", "1"));
    }
}
