package app.charka.attack;

import app.charka.encounter.EncounterService;
import app.charka.map.BattleFieldService;
import app.charka.map.BattleMapService;
import app.charka.map.MapDoc;
import app.charka.model.BattleMap;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("M-17: промах попадает в стоящего на линии огня (хоумбрю)")
class StrayShotTest {

    @Autowired private AttackService attackService;
    @Autowired private LineOfFire lineOfFire;
    @Autowired private BattleFieldService field;
    @Autowired private BattleMapService maps;
    @Autowired private EncounterService encounterService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character shooter;
    private Character target;
    private Character bystander;
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
        target = member("Цель");
        bystander = member("Прохожий");
        BattleMap map = maps.save(campaign.getId(), "Улица", MapDoc.empty(45, 5, 2));
        entityManager.flush();
        entityManager.clear();
        Long inventory = characterRepository.findById(shooter.getId()).orElseThrow().getInventories().get(0).getId();
        minami = itemService.addFromCatalog(inventory, "arasaka_minami_10", 1);
        encounterService.start(campaign.getId(), List.of(shooter.getId(), target.getId(), bystander.getId()), false, false, map.getId());
        entityManager.flush();
        entityManager.clear();
        // стрелок (0, 4), прохожий (2, 4) — в 4 м, цель (40, 4) — в 80 м
        place(shooter, 0);
        place(bystander, 2);
        place(target, 40);
        entityManager.flush();
        entityManager.clear();
    }

    private Character member(String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private void place(Character character, int x) {
        Long id = field.field(campaign.getId()).orElseThrow().tokens().stream()
                .filter(token -> token.character().equals(character.getId())).findFirst().orElseThrow().id();
        field.move(campaign.getId(), id, x, 4, true);
    }

    private int damage(Character character) {
        return characterRepository.findById(character.getId()).orElseThrow().getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType())).findFirst().orElseThrow().getValue();
    }

    private AttackResult shoot() {
        AttackResult result = attackService.attack(shooter.getId(), minami.getId(),
                new AttackRequest(target.getId(), null, 80, null, null, Set.of(), 0));
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    @Test
    @DisplayName("Линия огня по карте: кто между стрелком и целью и в скольких метрах")
    void between() {
        List<LineOfFire.Bystander> line = lineOfFire.between(characterRepository.findById(shooter.getId()).orElseThrow(),
                characterRepository.findById(target.getId()).orElseThrow());

        assertThat(line).extracting(b -> b.character().getName(), LineOfFire.Bystander::meters).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Прохожий", 4));
    }

    @Test
    @DisplayName("Промах по цели в 80 м (далеко, 25), бросок 15 — прохожий в 4 м близко (15): пуля в него")
    void strayHits() {
        // REF 6 + 9 = 15 < 25 (Minami — 150 м, 80 м — далеко); у прохожего 4 м — близко, сложность 15
        when(dice.roll(anyInt())).thenReturn(9, 5, 6, 6);

        AttackResult result = shoot();

        assertThat(result.hit()).as(String.join(" | ", result.steps())).isFalse();
        assertThat(result.outcome()).startsWith("Мимо цели, шальная пуля — Прохожий");
        assertThat(result.steps()).anyMatch(step -> step.contains("На линии огня — Прохожий в 4 м: бросок 15 ≥ 15"));
        assertThat(damage(bystander)).isPositive();
        assertThat(damage(target)).isZero();
    }

    @Test
    @DisplayName("Бросок ниже сложности до прохожего — пуля мимо всех")
    void strayMisses() {
        when(dice.roll(anyInt())).thenReturn(2, 5, 6, 6);

        AttackResult result = shoot();

        assertThat(result.hit()).isFalse();
        assertThat(result.steps()).anyMatch(step -> step.contains("пуля прошла мимо"));
        assertThat(damage(bystander)).isZero();
    }
}
