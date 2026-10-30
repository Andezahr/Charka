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
