package app.charka.system.kosmozeki;

import app.charka.attack.AttackRequest;
import app.charka.attack.AttackResult;
import app.charka.attack.AttackService;
import app.charka.attack.AttackSource;
import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.creation.CharacterCreation;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.service.CharacterService;
import app.charka.service.ItemService;
import app.charka.service.campaign.CampaignTimelineService;
import app.charka.service.counter.HealthCounterService;
import app.charka.system.GameSystemRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Система «Космозэки» (этап 15, {@code RULES_KOSMOZEKI.md}): кубик, тело по зонам, стрельба, патроны, лечение, создание.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Космозэки")
class KzSystemTest {

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CharacterRepository characterRepository;
    @Autowired private CharacterService characterService;
    @Autowired private ItemService itemService;
    @Autowired private AttackService attackService;
    @Autowired private HealthCounterService healthCounterService;
    @Autowired private CampaignTimelineService timelineService;
    @Autowired private GameSystemRegistry registry;
    @Autowired private List<CharacterCreation> creations;
    @Autowired private EntityManager entityManager;
    @Autowired private app.charka.undo.RerollService rerollService;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private DiceRoller dice;

    private Campaign campaign;
    private Character shooter;
    private Character target;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Каркосса");
        campaign.setSystem(KzSystem.CODE);
        campaign.setStartDate(LocalDate.of(2026, 1, 1));
        campaign.setCurrentDate(LocalDate.of(2026, 1, 1));
        campaign = campaignRepository.save(campaign);
        shooter = member("Бык");
        target = member("Гипс");
    }

    private Character member(String name) {
        Character character = new Character();
        character.setName(name);
        return characterService.createInCampaign(campaign.getId(), character);
    }

    private Counter body(Character character) {
        return KzAbilities.bodyCounter(characterRepository.findById(character.getId()).orElseThrow()).orElseThrow();
    }

    private int hp(Character character, String zone) {
        Counter body = body(character);
        return KzBodyStrategy.hp(body, KzBodyStrategy.body(body).zone(zone).orElseThrow());
    }

    /** Кубики к10 для к100: десятки, единицы (10 — это 0) */
    private void rollD100(int value) {
        int tens = value == 100 ? 10 : value / 10 == 0 ? 10 : value / 10;
        int units = value % 10 == 0 ? 10 : value % 10;
        when(dice.roll(anyInt())).thenReturn(tens, units);
    }

    private Item add(Character owner, String key) {
        Item item = itemService.addFromCatalog(owner.getInventories().get(0).getId(), key, 1);
        entityManager.flush();
        return item;
    }

    private static AttackRequest fire(Long targetId, int rounds) {
        return new AttackRequest(List.of(targetId), null, null, null, null, Set.of(), 0, KzAttackRules.FIRE, rounds, 0);
    }

    @Nested
    @DisplayName("Новый персонаж")
    class NewCharacter {

        @Test
        @DisplayName("Тело «Человек» и вдохновение создаются сами")
        void defaults() {
            Character fresh = characterRepository.findById(shooter.getId()).orElseThrow();
            assertThat(fresh.getCounters()).extracting(Counter::getType).contains(KzBodyStrategy.TYPE, "INSPIRATION");
            assertThat(hp(shooter, KzBody.HEAD)).isEqualTo(100);
            assertThat(hp(shooter, KzBody.BODY)).isEqualTo(150);
            assertThat(registry.get(KzSystem.CODE).getHealthStatus(fresh)).isEqualTo("Без ранений");
        }

        @Test
        @DisplayName("Мастер создания: теги и обязательные качества")
        void creation() {
            CharacterCreation creation = creations.stream().filter(c -> c.getSystemCode().equals(KzSystem.CODE)).findFirst().orElseThrow();
            Map<String, String> params = new HashMap<>(Map.of("name", "Олежа", "profile.adjective", "меткий", "profile.noun", "солдат",
                    "profile.verb", "поёт песни", "profile.sentence", "Кража рулета", "multi.qualities", "1",
                    "pick.qualities.Инициативность", "1", "role.SHOOTER", "1"));
            assertThatThrownBy(() -> creation.create(campaign, params)).hasMessageContaining("Командный дух");

            params.put("pick.qualities.Командный дух", "1");
            Character created = creation.create(campaign, params);

            assertThat(created.getProfile()).containsEntry("qualities", "Инициативность, Командный дух").containsEntry("sentence", "Кража рулета");
            assertThat(KzProfile.tags(created)).isEqualTo("Меткий солдат, который поёт песни");
            assertThat(created.getRoles()).containsExactly("SHOOTER");
        }
    }

    @Nested
    @DisplayName("Кубик к100")
    class Dice {

        @Test
        @DisplayName("Два к10: десятки и единицы, 0 и 0 — 100 (крит), 1 — критический провал")
        void d100() {
            KzCheckResolver resolver = new KzCheckResolver();
            rollD100(46);
            assertThat(resolver.resolve(new CheckRequest("x", 0, 40), dice).success()).isTrue();
            rollD100(100);
            CheckResult crit = resolver.resolve(new CheckRequest("x", 0, 200), dice);
            assertThat(crit.total()).isEqualTo(100);
            assertThat(crit.critical()).isTrue();
            assertThat(crit.success()).isTrue();
            rollD100(1);
            CheckResult fumble = resolver.resolve(new CheckRequest("x", 0, 0), dice);
            assertThat(fumble.fumble()).isTrue();
            assertThat(fumble.success()).isFalse();
        }
    }

    @Nested
    @DisplayName("Урон по зонам")
    class Damage {

        @Test
        @DisplayName("Пример правил: 3 пули по 20 в голову с бронёй 15 — каждая (20 − 15) × 2, итого 30")
        void armorPerBullet() {
            Item helmet = add(target, "kz-helmet");
            helmet.getProperties().put(KzItems.ARMOR_VALUE, "15");
            itemService.performAction(target.getId(), helmet.getId(), KzItemActions.WEAR, Map.of());
            entityManager.flush();

            HealthEvent event = healthCounterService.applyDamage(target.getId(), DamageRequest.builder()
                    .amount(20).location(KzBody.HEAD).extras(Map.of(KzBodyStrategy.HITS, 3)).build()).orElseThrow();

            assertThat(hp(target, KzBody.HEAD)).isEqualTo(70);
            assertThat(event.getSteps()).contains("Урон 20 − броня 15 = 5", "× 2 (Голова) = 10", "10 × 3 попаданий = 30");
        }

        @Test
        @DisplayName("Ближнее оружие: броня вдвое меньше; зона на нуле — без сознания, урон в неё пропадает")
        void halfArmorAndZero() {
            Item vest = add(target, "kz-vest");
            itemService.performAction(target.getId(), vest.getId(), KzItemActions.WEAR, Map.of());
            entityManager.flush();
            healthCounterService.applyDamage(target.getId(), DamageRequest.builder()
                    .amount(30).location(KzBody.BODY).extras(Map.of(KzBodyStrategy.ARMOR_HALF, 1)).build());
            assertThat(hp(target, KzBody.BODY)).isEqualTo(150 - (30 - 10));

            healthCounterService.applyDamage(target.getId(), DamageRequest.builder().amount(200).location(KzBody.LEFT_ARM).build());
            assertThat(KzBodyStrategy.unconscious(body(target))).isTrue();
            HealthEvent again = healthCounterService.applyDamage(target.getId(),
                    DamageRequest.builder().amount(50).location(KzBody.LEFT_ARM).build()).orElseThrow();
            assertThat(again.getSummary()).contains("уже на нуле");
            assertThat(hp(target, KzBody.BODY)).isEqualTo(130);
        }

        @Test
        @DisplayName("Набор зон мастера: свои названия, цифры и множители")
        void customZones() {
            Counter body = body(target);
            new KzBodyStrategy(dice).updateTexts(body, Map.of("zones", "Голова | 0 | 60 | 3\nПанцирь | 1-9 | 300"));
            assertThat(KzBodyStrategy.body(body).zones()).extracting(KzBody.Zone::name).containsExactly("Голова", "Панцирь");
            assertThat(KzBodyStrategy.body(body).byDigit(7).orElseThrow().maxHp()).isEqualTo(300);
            assertThat(KzBodyStrategy.body(body).zone(KzBody.HEAD).orElseThrow().multiplier()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Стрельба")
    class Shooting {

        @Test
        @DisplayName("Пистолет, 2 пули: DIF 30 − 2 = 28, бросок 46 — 2 попадания (не больше пуль) в тело, патроны списаны")
        void pistol() {
            Item pistol = add(shooter, "kz-pistol");
            rollD100(46);

            AttackResult result = attackService.attack(shooter.getId(), pistol.getId(), fire(target.getId(), 2));

            assertThat(result.hit()).isTrue();
            assertThat(result.difficulty()).isEqualTo(28);
            assertThat(hp(target, KzBody.BODY)).isEqualTo(150 - 40);
            assertThat(pistol.getProperties()).containsEntry(KzItems.LOADED, "5");
        }

        @Test
        @DisplayName("Винтовка, 30 пуль: сложность 20, бросок 46 — 3 попадания")
        void rifle() {
            Item rifle = add(shooter, "kz-rifle");
            rollD100(46);

            AttackResult result = attackService.attack(shooter.getId(), rifle.getId(), fire(target.getId(), 30));

            assertThat(result.steps()).anyMatch(step -> step.contains("= 3"));
            assertThat(hp(target, KzBody.BODY)).isEqualTo(150 - 60);
        }

        @Test
        @DisplayName("Прицельно в голову +30; 100 — крит в голову с доп. уроном")
        void aimedAndCrit() {
            Item pistol = add(shooter, "kz-pistol");
            rollD100(100);
            AttackRequest aimed = new AttackRequest(List.of(target.getId()), null, null, KzBody.LEFT_LEG, null, Set.of(), 0, KzAttackRules.FIRE, 1, 0);

            AttackResult result = attackService.attack(shooter.getId(), pistol.getId(), aimed);

            assertThat(result.difficulty()).isEqualTo(30 + 15 - 1);
            // крит: в голову, 1 попадание (20 × 2) + ещё урон пули 20
            assertThat(hp(target, KzBody.HEAD)).isEqualTo(100 - 40 - 20);
        }

        @Test
        @DisplayName("«Сложность вдвое» раз за бой: выгоднейший вариант, второй раз нельзя")
        void halve() {
            Character fresh = characterRepository.findById(shooter.getId()).orElseThrow();
            Counter halve = new Counter();
            halve.setType(KzHalveStrategy.TYPE);
            halve.setName("Сложность вдвое");
            halve.setValue(1);
            halve.setCharacter(fresh);
            fresh.getCounters().add(halve);
            entityManager.flush();
            Item rifle = add(shooter, "kz-rifle");
            rollD100(10);
            AttackRequest request = new AttackRequest(List.of(target.getId()), null, null, null, null, Set.of(KzAttackRules.HALVE), 0,
                    KzAttackRules.FIRE, 10, 0);

            AttackResult result = attackService.attack(shooter.getId(), rifle.getId(), request);

            // база пополам: 25 − 10 = 15; итог пополам: (50 − 10) / 2 = 20 → 15
            assertThat(result.difficulty()).isEqualTo(15);
            assertThat(halve.getValue()).isZero();
            assertThatThrownBy(() -> attackService.attack(shooter.getId(), rifle.getId(), request)).hasMessageContaining("потрачена");
        }

        @Test
        @DisplayName("Перезарядка из запаса подходящих патронов")
        void reload() {
            Item pistol = add(shooter, "kz-pistol");
            pistol.getProperties().put(KzItems.LOADED, "1");
            Item ammo = add(shooter, "kz-ammo-Пистолетные");
            entityManager.flush();

            String result = itemService.performAction(shooter.getId(), pistol.getId(), KzItemActions.RELOAD, Map.of());

            assertThat(result).contains("7/7");
            assertThat(ammo.getQuantity()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Лечение и способности")
    class Healing {

        @Test
        @DisplayName("Сон и еда: +60 в каждую зону за день; «Жидкая кожа»: +50 в самую раненую зону")
        void rest() {
            healthCounterService.applyDamage(target.getId(), DamageRequest.builder().amount(100).location(KzBody.BODY).build());
            healthCounterService.applyDamage(target.getId(), DamageRequest.builder().amount(90).location(KzBody.LEFT_ARM).build());
            timelineService.proceedDate(campaign.getId(), 1, "Отдых");
            assertThat(hp(target, KzBody.BODY)).isEqualTo(110);
            assertThat(hp(target, KzBody.LEFT_ARM)).isEqualTo(70);

            Item skin = add(target, "kz-skin");
            itemService.performAction(target.getId(), skin.getId(), KzItemActions.HEAL_ONE, Map.of());
            assertThat(hp(target, KzBody.LEFT_ARM)).isEqualTo(100);
        }

        @Test
        @DisplayName("Инициатива 1к12; отложенный кубик предсказания заменяет бросок")
        void portent() {
            Character fresh = characterRepository.findById(shooter.getId()).orElseThrow();
            Counter portent = new Counter();
            portent.setType(KzPortentStrategy.TYPE);
            portent.setName("Предсказание");
            portent.setCharacter(fresh);
            portent.getAttributes().putAll(Map.of("die_1", 11, "die_2", 3, "die_3", 7));
            fresh.getCounters().add(portent);
            when(dice.roll(anyInt())).thenReturn(5);

            new KzPortentStrategy(dice).performAction(new app.charka.model.counters.strategy.CounterContext(portent, List.of(portent)), "USE_1");
            assertThat(registry.get(KzSystem.CODE).rollInitiative(fresh, dice).total()).isEqualTo(11);
            assertThat(registry.get(KzSystem.CODE).rollInitiative(fresh, dice).total()).isEqualTo(5);
        }

        @Test
        @DisplayName("Переброс за вдохновение: только атака, проверка или бросок; вдохновение −1, без него — отказ")
        void reroll() {
            Counter inspiration = KzAbilities.counter(characterRepository.findById(shooter.getId()).orElseThrow(), "INSPIRATION").orElseThrow();
            inspiration.setValue(1);
            String path = "/character/" + shooter.getId() + "/check";

            assertThat(rerollService.prepare(shooter.getId(), null, path)).isEqualTo(path);
            assertThat(inspiration.getValue()).isZero();
            assertThatThrownBy(() -> rerollService.prepare(shooter.getId(), null, path)).hasMessageContaining("Вдохновения нет");
            assertThatThrownBy(() -> rerollService.prepare(shooter.getId(), null, "/character/" + shooter.getId() + "/money"))
                    .hasMessageContaining("не перебросить");
        }

        @Test
        @DisplayName("Драка: 1к12 против 1к12, больше — попал, урон вводит атакующий")
        void brawl() {
            when(dice.roll(anyInt())).thenReturn(9, 4);
            AttackRequest punch = new AttackRequest(List.of(target.getId()), null, null, KzBody.HEAD, null, Set.of(), 0, KzAttackRules.BRAWL, 15, 0);

            AttackResult result = attackService.attack(shooter.getId(), AttackSource.ability(KzAttackRules.BRAWL, "Драка"), punch);

            assertThat(result.hit()).isTrue();
            assertThat(hp(target, KzBody.HEAD)).isEqualTo(70);
        }
    }

    @Autowired private app.charka.check.CheckService checkService;
    @Autowired private app.charka.request.RollRequestService rollRequestService;
    @Autowired private app.charka.encounter.EncounterService encounterService;
    @Autowired private app.charka.map.BattleFieldService battleFieldService;
    @Autowired private app.charka.map.MapLootService mapLootService;

    @Nested
    @DisplayName("Исправления после игры (KZ_FIX.md)")
    class Fixes {

        @Test
        @DisplayName("KF-09: проверка за столом — 1к12 против сложности, попадание в бою — по-прежнему к100")
        void tableCheck() {
            when(dice.roll(anyInt())).thenReturn(9);
            CheckResult table = checkService.tableCheckForCharacter(shooter.getId(), new CheckRequest("Взлом", 1, 10));

            assertThat(table.notation()).isEqualTo("1d12+1");
            assertThat(table.total()).isEqualTo(10);
            assertThat(table.success()).isTrue();
            assertThat(registry.get(KzSystem.CODE).getTableCheckResolver().getDifficultyPresets()).isEmpty();

            rollD100(42);
            assertThat(checkService.checkForCharacter(shooter.getId(), new CheckRequest("Попадание", 0, 30)).notation()).startsWith("d100");
        }

        @Test
        @DisplayName("KF-01: вплотную — только нож и драка; пистолет и граната — нет")
        void melee() {
            app.charka.attack.AttackRules rules = registry.get(KzSystem.CODE).getAttackRules();
            assertThat(rules.form(AttackSource.of(add(shooter, "kz-knife")), Map.of()).isMelee()).isTrue();
            assertThat(rules.form(AttackSource.ability(KzAttackRules.BRAWL, "Драка"), Map.of()).isMelee()).isTrue();
            assertThat(rules.form(AttackSource.of(add(shooter, "kz-pistol")), Map.of()).isMelee()).isFalse();
            assertThat(rules.form(AttackSource.of(add(shooter, "kz-grenade")), Map.of()).isMelee()).isFalse();
        }

        @Test
        @DisplayName("KF-02: патроны выбираются из списка")
        void caliberList() {
            assertThat(KzItems.RANGED_TYPE.fields().stream().filter(field -> field.key().equals(KzItems.CALIBER)).findFirst().orElseThrow()
                    .isSuggested()).isTrue();
            assertThat(KzItems.AMMO_TYPE.fields().get(0).isSuggested()).isTrue();
        }

        @Test
        @DisplayName("KF-10: без навыков мастер просит простую проверку — бросок 1к12 против сложности")
        void requestCheck() {
            assertThat(rollRequestService.choices(campaign.getId()).get(0).value()).isEqualTo(app.charka.request.RollRequestService.CHECK_PREFIX);
            when(dice.roll(anyInt())).thenReturn(8);
            entityManager.flush();
            entityManager.clear();

            List<String> results = rollRequestService.ask(campaign.getId(), List.of(shooter.getId()),
                    app.charka.request.RollRequestService.CHECK_PREFIX, 7, true, false, "Взлом замка");

            assertThat(results).singleElement().asString().contains("Взлом замка 7: 8 — успех");
        }

        @Test
        @DisplayName("KF-05: предмет на карте — подобрать с соседней клетки, оружие становится основным")
        void loot() {
            encounterService.start(campaign.getId(), List.of(shooter.getId(), target.getId()));
            Long participant = encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                    .filter(row -> row.character().getId().equals(shooter.getId())).findFirst().orElseThrow().participant().getId();
            battleFieldService.move(campaign.getId(), participant, 5, 5, true);
            mapLootService.place(campaign.getId(), "kz-knife", 1, 6, 6, 0);
            mapLootService.place(campaign.getId(), "kz-pistol", 1, 9, 9, 0);
            entityManager.flush();
            List<app.charka.map.BattleFieldService.Loot> loot = battleFieldService.field(campaign.getId()).orElseThrow().loot();
            assertThat(loot).extracting(app.charka.map.BattleFieldService.Loot::name).containsExactly("Нож", "Стандартный пистолет");

            assertThatThrownBy(() -> mapLootService.take(campaign.getId(), loot.get(1).id(), participant)).hasMessageContaining("Далеко");
            assertThat(mapLootService.take(campaign.getId(), loot.get(0).id(), participant)).contains("основное оружие");
            entityManager.flush();
            entityManager.clear();

            Character owner = characterRepository.findById(shooter.getId()).orElseThrow();
            Item knife = owner.getInventories().stream().flatMap(inventory -> inventory.getContent().stream())
                    .filter(item -> item.getName().equals("Нож")).findFirst().orElseThrow();
            assertThat(owner.getMainWeaponId()).isEqualTo(knife.getId());
            assertThat(battleFieldService.field(campaign.getId()).orElseThrow().loot()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Одно действие за ход")
    class OneAction {

        @Test
        @DisplayName("В бою вторая атака и перезарядка в том же раунде — отказ; новый раунд — снова можно")
        void secondAttackRefused() {
            Item pistol = add(shooter, "kz-pistol");
            encounterService.start(campaign.getId(), List.of(shooter.getId(), target.getId()));
            entityManager.flush();
            rollD100(46);
            attackService.attack(shooter.getId(), pistol.getId(), fire(target.getId(), 1));
            entityManager.flush();

            assertThatThrownBy(() -> attackService.attack(shooter.getId(), pistol.getId(), fire(target.getId(), 1)))
                    .hasMessage(app.charka.encounter.RoundActions.NO_ACTIONS_LEFT);
            assertThatThrownBy(() -> itemService.performAction(shooter.getId(), pistol.getId(), KzItemActions.RELOAD, Map.of()))
                    .hasMessage(app.charka.encounter.RoundActions.NO_ACTIONS_LEFT);
            assertThat(pistol.getProperties()).containsEntry(KzItems.LOADED, "6");
            assertThat(encounterService.view(campaign.getId()).orElseThrow().rows().stream()
                    .filter(row -> row.character().getId().equals(shooter.getId())).findFirst().orElseThrow().canAct()).isFalse();

            encounterService.next(campaign.getId());
            encounterService.next(campaign.getId());
            entityManager.flush();
            rollD100(46);

            assertThat(attackService.attack(shooter.getId(), pistol.getId(), fire(target.getId(), 1)).hit()).isTrue();
        }

        @Test
        @DisplayName("Вне боя предела нет")
        void outsideCombat() {
            Item pistol = add(shooter, "kz-pistol");
            rollD100(46);
            attackService.attack(shooter.getId(), pistol.getId(), fire(target.getId(), 1));
            rollD100(46);

            assertThat(attackService.attack(shooter.getId(), pistol.getId(), fire(target.getId(), 1)).hit()).isTrue();
        }
    }
}
