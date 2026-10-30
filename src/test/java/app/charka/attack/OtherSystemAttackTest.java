package app.charka.attack;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.item.ItemField.Option;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CampaignRepository;
import app.charka.service.CharacterService;
import app.charka.service.InventoryService;
import app.charka.service.ItemService;
import app.charka.skill.StatValue;
import app.charka.system.Currency;
import app.charka.system.GameSystem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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

/**
 * Проверка, что контракт атаки не привязан к Cyberpunk 2020: система в духе D&D 5e, только в тестах.
 * Сложность задаёт цель (класс брони), заклинание — не предмет, расстояния нет, огненный шар — спасбросок на половину урона.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(OtherSystemAttackTest.Config.class)
@DisplayName("Контракт атаки подходит другой системе")
class OtherSystemAttackTest {

    private static final String CODE = "TEST_D20";

    @TestConfiguration
    static class Config {
        @Bean
        GameSystem d20System() {
            return new D20System();
        }
    }

    static class D20System implements GameSystem {
        private static final AttackRules RULES = new D20Rules();

        @Override
        public String getCode() {
            return CODE;
        }

        @Override
        public String getDisplayName() {
            return "Тестовый d20";
        }

        @Override
        public Currency getCurrency() {
            return new Currency("Золото", "зм");
        }

        @Override
        public Map<String, StatValue> getCurrentStats(Character character) {
            return Map.of("dex", new StatValue("dex", "DEX", 2, 2));
        }

        @Override
        public AttackRules getAttackRules() {
            return RULES;
        }
    }

    static class D20Rules implements AttackRules {

        /** Класс брони живёт в системе; здесь число для теста */
        private static final int ARMOR_CLASS = 13;

        @Override
        public AttackForm form(AttackSource source, Map<String, StatValue> stats) {
            AttackForm.Mode mode = new AttackForm.Mode("ONE", "Обычная", false, false, source.item() == null, false, false);
            return new AttackForm("1d8", null, null, List.of(mode), 1, 1, "", List.<Option>of(), null, List.of(), null, List.of(), List.of(), "Атаковать");
        }

        @Override
        public AttackPlan plan(AttackContext context, DiceRoller dice) {
            if (context.item() != null) {
                Character target = context.targets().get(0);
                CheckRequest attack = new CheckRequest("Атака", 5, ARMOR_CLASS);
                return new AttackPlan(context.source().name() + " → " + target.getName(),
                        List.of(new AttackPlan.PlannedCheck(attack, context.attacker(), target, null)), null);
            }
            int damage = 0;
            for (int i = 0; i < 8; i++) {
                damage += dice.roll(6);
            }
            AreaDamage blast = new AreaDamage(context.source().name(), "8d6", damage, 6, Map.of(), List.of());
            List<AttackPlan.PlannedCheck> saves = new ArrayList<>();
            for (Character target : context.targets()) {
                saves.add(new AttackPlan.PlannedCheck(new CheckRequest("Спасбросок DEX", 2, 15), target, target, target.getName() + ": спасбросок"));
            }
            return new AttackPlan(context.source().name(), saves, blast);
        }

        @Override
        public String spend(AttackContext context) {
            return context.item() == null ? "Потрачена ячейка 3 уровня" : null;
        }

        @Override
        public AttackOutcome outcome(AttackContext context, AttackPlan plan, AttackPlan.PlannedCheck planned, CheckResult result, DiceRoller dice) {
            boolean success = Boolean.TRUE.equals(result.success());
            if (context.item() != null) {
                if (!success) {
                    return AttackOutcome.miss(List.of(), "Мимо");
                }
                return AttackOutcome.hits(List.of(), List.of(new AttackOutcome.TargetedHit(planned.target(), hit(dice.roll(8) + 3))), null, true);
            }
            int amount = success ? plan.blast().amount() / 2 : plan.blast().amount();
            return AttackOutcome.hits(List.of(), List.of(new AttackOutcome.TargetedHit(planned.target(), hit(amount))), null, true);
        }

        private static AttackHit hit(int amount) {
            return new AttackHit("тело", "урон", List.of("Урон " + amount), DamageRequest.builder().amount(amount).build());
        }
    }

    @Autowired private AttackService attackService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private InventoryService inventoryService;
    @Autowired private EntityManager entityManager;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Character wizard;
    private Character orc;
    private Character goblin;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Подземелье");
        campaign.setSystem(CODE);
        campaign.setStartDate(LocalDate.of(1490, 1, 1));
        campaign.setCurrentDate(LocalDate.of(1490, 1, 1));
        campaign = campaignRepository.save(campaign);
        wizard = member(campaign, "Маг");
        orc = member(campaign, "Орк");
        goblin = member(campaign, "Гоблин");
    }

    private Character member(Campaign campaign, String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private static AttackRequest noDistance(List<Long> targets) {
        return new AttackRequest(targets, null, null, null, null, Set.of(), 0, "ONE", 0, 0);
    }

    @Test
    @DisplayName("Меч: сложность — класс брони цели, расстояние не нужно, предмет не тратится")
    void longsword() {
        Item sword = new Item();
        sword.setName("Длинный меч");
        sword.setQuantity(1);
        sword = itemService.create(wizard.getInventories().get(0).getId(), sword);
        entityManager.flush();
        when(dice.roll(anyInt())).thenReturn(10, 4);

        AttackResult result = attackService.attack(wizard.getId(), sword.getId(), noDistance(List.of(orc.getId())));

        assertThat(result.hit()).isTrue();
        assertThat(result.title()).isEqualTo("Длинный меч → Орк");
        assertThat(result.steps()).anyMatch(step -> step.contains("13")).anyMatch(step -> step.contains("Урон 7"));
    }

    @Test
    @DisplayName("Огненный шар: не предмет, ресурс не патроны, один бросок урона, у спасшегося половина")
    void fireball() {
        // 8d6 по 4 = 32; у Орка на спасброске 1, у Гоблина 18
        when(dice.roll(anyInt())).thenReturn(4, 4, 4, 4, 4, 4, 4, 4, 1, 18);

        AttackResult result = attackService.attack(wizard.getId(), AttackSource.ability("fireball", "Огненный шар"),
                noDistance(List.of(orc.getId(), goblin.getId())));

        assertThat(result.steps()).contains("Потрачена ячейка 3 уровня", "Орк: спасбросок", "Гоблин: спасбросок")
                .anyMatch(step -> step.equals("Урон 32"))
                .anyMatch(step -> step.equals("Урон 16"));
        assertThat(result.outcome()).contains("2 из 2");
    }
}
