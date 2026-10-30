package app.charka.attack;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.ItemRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.system.cp2020.Cp2020ArmorStrategy;
import app.charka.system.cp2020.Cp2020HealthStrategy;
import app.charka.system.cp2020.Cp2020ItemTypes;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Атака: весь ход на настоящих сервисах")
class AttackServiceTest {

    @Autowired private AttackService attackService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private ItemRepository itemRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Character shooter;
    private Character victim;
    private Item minami;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Тест");
        campaign.setSystem("CP2020");
        campaign.setStartDate(LocalDate.of(2037, 1, 1));
        campaign.setCurrentDate(LocalDate.of(2037, 1, 1));
        campaign = campaignRepository.save(campaign);

        shooter = member(campaign, "Стрелок");
        victim = member(campaign, "Цель");
        entityManager.flush();
        entityManager.clear();
        Long inventoryId = characterRepository.findById(shooter.getId()).orElseThrow().getInventories().get(0).getId();
        minami = itemService.addFromCatalog(inventoryId, "arasaka_minami_10", 1);
        Long victimInventory = characterRepository.findById(victim.getId()).orElseThrow().getInventories().get(0).getId();
        Item jacket = itemService.addFromCatalog(victimInventory, "armor_light_jacket", 1);
        itemService.performAction(victim.getId(), jacket.getId(), "WEAR", java.util.Map.of());
        entityManager.flush();
        entityManager.clear();
    }

    private Character member(Campaign campaign, String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private Counter counter(Character character, String type) {
        return characterRepository.findById(character.getId()).orElseThrow().getCounters().stream()
                .filter(c -> type.equals(c.getType()))
                .findFirst().orElseThrow();
    }

    private AttackRequest atTorso() {
        return new AttackRequest(victim.getId(), null, 5, "TORSO", null, Set.of("immobile"), 0);
    }

    @Test
    @DisplayName("Попадание меняет здоровье и броню цели, расход патрона, расчёт виден целиком")
    void hitChangesTarget() {
        when(dice.roll(anyInt())).thenReturn(9, 6, 6);
        // REF 6 + навык 0 + WA 0 + неподвижна 4 − прицельный 4 + 9 = 15 против 15; урон 6 + 6 + 3 = 15
        AttackResult result = attackService.attack(shooter.getId(), minami.getId(), atTorso());
        entityManager.flush();
        entityManager.clear();

        assertThat(result.hit()).isTrue();
        assertThat(result.steps()).anyMatch(step -> step.contains("Сложность 15"))
                .anyMatch(step -> step.contains("Урон 2d6+3"))
                .anyMatch(step -> step.contains("Урон 15 − SP 14"))
                .anyMatch(step -> step.contains("Броня пробита"));
        assertThat(counter(victim, Cp2020HealthStrategy.TYPE).getValue()).isEqualTo(1);
        Item jacket = characterRepository.findById(victim.getId()).orElseThrow().getInventories().get(0).getContent().stream()
                .filter(item -> Cp2020ItemTypes.ARMOR.equals(item.getType())).findFirst().orElseThrow();
        assertThat(jacket.getProperties()).containsEntry("wear_TORSO", "1");
        assertThat(itemRepository.findById(minami.getId()).orElseThrow().getProperties()).containsEntry("ammo", "39");
        assertThat(counter(victim, Cp2020ArmorStrategy.TYPE)).isNotNull();
    }

    @Test
    @DisplayName("Очередь не падает, если цель умерла на одной из пуль")
    void targetDiesMidBurst() {
        counter(victim, Cp2020HealthStrategy.TYPE).getAttributes().put(Cp2020HealthStrategy.DEAD, 1);
        entityManager.flush();
        when(dice.roll(anyInt())).thenReturn(9, 6, 6);

        AttackResult result = attackService.attack(shooter.getId(), minami.getId(),
                new AttackRequest(List.of(victim.getId()), null, 5, null, null, Set.of("immobile"), 0, "AUTO", 20, 0));

        assertThat(result.steps()).contains("Цель уже мертва");
        assertThat(result.outcome()).contains("Цель уже мертва");
    }

    @Test
    @DisplayName("Подавляющий огонь: каждая цель бросает сама, патроны списаны один раз")
    void zoneFire() {
        when(dice.roll(anyInt())).thenReturn(9);

        AttackResult result = attackService.attack(shooter.getId(), minami.getId(),
                new AttackRequest(List.of(victim.getId()), null, 20, null, null, Set.of(), 0, "SUPPRESS", 20, 2));
        entityManager.flush();
        entityManager.clear();

        assertThat(result.title()).endsWith("зона огня");
        assertThat(result.steps()).anyMatch(step -> step.startsWith("Цель: спасбросок"));
        assertThat(itemRepository.findById(minami.getId()).orElseThrow().getProperties()).containsEntry("ammo", "20");
    }

    @Test
    @DisplayName("Промах расходует патрон и не трогает цель")
    void missSpendsRound() {
        when(dice.roll(anyInt())).thenReturn(2);

        AttackResult result = attackService.attack(shooter.getId(), minami.getId(), atTorso());
        entityManager.flush();
        entityManager.clear();

        assertThat(result.hit()).isFalse();
        assertThat(counter(victim, Cp2020HealthStrategy.TYPE).getValue()).isZero();
        assertThat(itemRepository.findById(minami.getId()).orElseThrow().getProperties()).containsEntry("ammo", "39");
    }

    @Test
    @DisplayName("Цель без листа: урон брошен, применяет мастер")
    void targetWithoutSheet() {
        when(dice.roll(anyInt())).thenReturn(9, 3, 4);

        AttackResult result = attackService.attack(shooter.getId(), minami.getId(),
                new AttackRequest(null, "Бустер", 5, "TORSO", null, Set.of("immobile"), 0));

        assertThat(result.title()).endsWith("Бустер");
        assertThat(result.steps()).anyMatch(step -> step.contains("урон применяет мастер"));
    }

    private Item fromCatalog(String key, int quantity) {
        Long inventoryId = characterRepository.findById(shooter.getId()).orElseThrow().getInventories().get(0).getId();
        Item item = itemService.addFromCatalog(inventoryId, key, quantity);
        entityManager.flush();
        entityManager.clear();
        return item;
    }

    @Test
    @DisplayName("Брошенная граната попала: задеты выбранные цели, последняя граната ушла из инвентаря")
    void grenadeHits() {
        Item grenade = fromCatalog("grenade_fragmentation", 1);
        // 7D6 по 3 = 21; бросок 9; зона: торс. Урон 21 − SP 14 = 7
        when(dice.roll(anyInt())).thenReturn(3, 3, 3, 3, 3, 3, 3, 9, 3);

        AttackResult result = attackService.attack(shooter.getId(), grenade.getId(),
                new AttackRequest(List.of(victim.getId()), null, 5, null, null, Set.of(), 0, "THROW", 0, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(result.hit()).isTrue();
        assertThat(result.detached()).isTrue();
        assertThat(result.scattered()).isNull();
        assertThat(result.outcome()).contains("Зона 5 м");
        assertThat(counter(victim, Cp2020HealthStrategy.TYPE).getValue()).isPositive();
        assertThat(itemRepository.findById(grenade.getId())).isEmpty();
    }

    @Test
    @DisplayName("Промах: заряд отклонился, никого не задело, пока мастер не выберет")
    void grenadeScatters() {
        Item grenade = fromCatalog("grenade_fragmentation", 2);
        // 7D6 по 3; бросок 2 — мимо; отклонение 8, 4 м; при применении зона: торс
        when(dice.roll(anyInt())).thenReturn(3, 3, 3, 3, 3, 3, 3, 2, 8, 4, 3);

        AttackResult result = attackService.attack(shooter.getId(), grenade.getId(),
                new AttackRequest(List.of(victim.getId()), null, 5, null, null, Set.of(), 0, "THROW", 0, 0));

        assertThat(result.hit()).isFalse();
        assertThat(result.detached()).isFalse();
        assertThat(result.scattered().amount()).isEqualTo(21);
        assertThat(result.steps()).anyMatch(step -> step.startsWith("Отклонение"));
        assertThat(counter(victim, Cp2020HealthStrategy.TYPE).getValue()).isZero();

        AttackResult applied = attackService.applyBlast(shooter.getId(), result.scattered(), List.of(victim.getId()));
        entityManager.flush();
        entityManager.clear();

        assertThat(applied.detached()).isTrue();
        assertThat(applied.outcome()).contains("Задето целей: 1");
        assertThat(counter(victim, Cp2020HealthStrategy.TYPE).getValue()).isPositive();
        assertThat(itemRepository.findById(grenade.getId()).orElseThrow().getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Применить взрыв можно только к выбранным целям той же кампании")
    void blastNeedsTargets() {
        AreaDamage blast = new AreaDamage("Граната", "7d6", 21, "NORMAL", false, 1, 5, List.of());

        assertThatThrownBy(() -> attackService.applyBlast(shooter.getId(), blast, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("кого задело");
        assertThatThrownBy(() -> attackService.applyBlast(shooter.getId(), blast, List.of(shooter.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Мина подрывается без броска на попадание")
    void minePlanted() {
        Item mine = fromCatalog("mine_all_types", 2);
        when(dice.roll(anyInt())).thenReturn(5, 5, 5, 5, 3);

        AttackResult result = attackService.attack(shooter.getId(), mine.getId(),
                new AttackRequest(List.of(victim.getId()), null, 0, null, null, Set.of(), 0, "PLANT", 0, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(result.hit()).isTrue();
        assertThat(result.steps()).noneMatch(step -> step.contains("Сложность"));
        assertThat(counter(victim, Cp2020HealthStrategy.TYPE).getValue()).isPositive();
        assertThat(itemRepository.findById(mine.getId()).orElseThrow().getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Метательное бьёт как обычная атака и расходуется")
    void shurikenThrown() {
        Item shiriken = fromCatalog("shiriken", 3);
        when(dice.roll(anyInt())).thenReturn(9, 3, 6);

        AttackResult result = attackService.attack(shooter.getId(), shiriken.getId(),
                new AttackRequest(List.of(victim.getId()), null, 3, null, null, Set.of("immobile"), 0, "SINGLE", 0, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(result.detached()).isFalse();
        assertThat(result.steps()).anyMatch(step -> step.contains("Атлетика"));
        assertThat(itemRepository.findById(shiriken.getId()).orElseThrow().getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Дробовик задевает всех выбранных в пятне, патрон один")
    void shotgunPattern() {
        Long inventoryId = characterRepository.findById(shooter.getId()).orElseThrow().getInventories().get(0).getId();
        Item shotgun = itemService.addFromCatalog(inventoryId, "arasaka_rapid_assault_12", 1);
        entityManager.flush();
        entityManager.clear();
        when(dice.roll(anyInt())).thenReturn(6, 6, 6, 6, 9, 3);

        AttackResult result = attackService.attack(shooter.getId(), shotgun.getId(),
                new AttackRequest(List.of(victim.getId()), null, 5, null, null, Set.of("immobile"), 0, "SINGLE", 0, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(result.outcome()).contains("Зона 1 м");
        assertThat(counter(victim, Cp2020HealthStrategy.TYPE).getValue()).isPositive();
        assertThat(itemRepository.findById(shotgun.getId()).orElseThrow().getProperties()).containsEntry("ammo", "19");
    }

    @Test
    @DisplayName("Нельзя стрелять в себя, в чужую кампанию и чужим оружием")
    void refused() {
        assertThatThrownBy(() -> attackService.attack(shooter.getId(), minami.getId(),
                new AttackRequest(shooter.getId(), null, 5, null, null, Set.of(), 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attackService.attack(victim.getId(), minami.getId(), atTorso()))
                .isInstanceOf(java.util.NoSuchElementException.class);

        Campaign other = new Campaign();
        other.setName("Другая");
        other.setSystem("CP2020");
        other.setStartDate(LocalDate.of(2037, 1, 1));
        other.setCurrentDate(LocalDate.of(2037, 1, 1));
        Character stranger = member(campaignRepository.save(other), "Чужой");
        assertThatThrownBy(() -> attackService.attack(shooter.getId(), minami.getId(),
                new AttackRequest(stranger.getId(), null, 5, null, null, Set.of(), 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("кампании");
    }
}
