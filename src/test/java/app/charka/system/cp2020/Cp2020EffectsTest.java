package app.charka.system.cp2020;

import app.charka.attack.AreaDamage;
import app.charka.attack.AttackForm;
import app.charka.attack.AttackHit;
import app.charka.attack.AttackRequest;
import app.charka.check.CheckRequest;
import app.charka.item.CatalogItem;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.skill.StatValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Особые заряды CP2020")
class Cp2020EffectsTest {

    private static final Map<String, StatValue> STATS = Map.of(
            "ref", new StatValue("ref", "REF", 8, 7),
            "body", new StatValue("body", "BODY", 6, 6));

    private final Cp2020AttackRules rules = new Cp2020AttackRules();

    private Item weapon(String key, int quantity) {
        CatalogItem entry = Cp2020Weapons.CATALOG.stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
        Inventory inventory = new Inventory();
        inventory.setContent(new ArrayList<>());
        Item item = new Item();
        item.setName(entry.name());
        item.setQuantity(quantity);
        item.setType(entry.typeCode());
        item.getProperties().putAll(entry.properties());
        item.setInventory(inventory);
        inventory.getContent().add(item);
        return item;
    }

    private static AttackRequest shot(String mode, int distance, int rounds) {
        return new AttackRequest(List.of(), null, distance, null, null, Set.of(), 0, mode, rounds, 0);
    }

    private AreaDamage blast(String key, String mode) {
        return Cp2020AreaAttacks.areaDamage(weapon(key, 1), shot(mode, 5, 1), sides -> 1);
    }

    @Nested
    @DisplayName("Ракеты, РПГ, огнемёт: выстрел по площади")
    class Launchers {

        @Test
        @DisplayName("Зоны по таблице Area Effect (с. 99)")
        void radius() {
            assertThat(blast("scorpion_16_missile_launcher", "SINGLE").radius()).isEqualTo(6);
            assertThat(blast("militech_arms_rpg_a", "SINGLE").radius()).isEqualTo(4);
            assertThat(blast("k_a_f_253_flamethrower", "SINGLE").radius()).isEqualTo(2);
        }

        @Test
        @DisplayName("Цели отмечают несколько, зону не выбирают")
        void mode() {
            AttackForm.Mode mode = rules.form(weapon("militech_arms_rpg_a", 1), STATS).modes().get(0);

            assertThat(mode.multiTarget()).isTrue();
            assertThat(mode.chooseLocation()).isFalse();
        }

        @Test
        @DisplayName("Навык — тяжёлое оружие, сложность по дальности оружия")
        void toHit() {
            CheckRequest check = rules.toHit(new Character(), weapon("militech_arms_rpg_a", 1), shot("SINGLE", 100, 0), STATS);

            assertThat(check.difficulty()).isEqualTo(15);
            assertThat(check.parts()).extracting(CheckRequest.Part::name).contains("Тяжёлое оружие");
        }

        @Test
        @DisplayName("Заряд берётся из запаса своего вида: в справочнике есть и ракета, и заряд РПГ")
        void ammo() {
            assertThat(Cp2020Ammo.CATALOG).extracting(row -> row.properties().get(Cp2020ItemTypes.CALIBER))
                    .contains(Cp2020Ammo.MISSILES, Cp2020Ammo.RPG_ROUNDS);
            assertThat(weapon("militech_arms_rpg_a", 1).getProperties()).containsKey(Cp2020ItemTypes.DIRECT_LOAD);
        }
    }

    @Nested
    @DisplayName("Взрывчатка книги (с. 111)")
    class Explosives {

        @Test
        @DisplayName("Зона на единицу и своя единица в форме")
        void units() {
            Item tnt = weapon("tnt", 4);

            assertThat(Cp2020AreaAttacks.areaDamage(tnt, shot("PLANT", 0, 4), sides -> 1).radius()).isEqualTo(12);
            assertThat(rules.form(tnt, STATS).roundsLabel()).contains("шашек");
        }

        @Test
        @DisplayName("Молотов только метают, 2 м на литр, урон по всему телу")
        void molotov() {
            Item molotov = weapon("molotov_cocktail", 2);

            assertThat(rules.form(molotov, STATS).modes()).extracting(AttackForm.Mode::code).containsExactly("THROW");
            AreaDamage blast = Cp2020AreaAttacks.areaDamage(molotov, shot("THROW", 5, 2), sides -> 1);
            assertThat(blast.radius()).isEqualTo(4);
            assertThat(blast.data()).containsEntry("wholeBody", "true");
        }

        @Test
        @DisplayName("Клеймор: 6 м, подрывают на месте")
        void claymore() {
            Item claymore = weapon("claymore_mine", 1);

            assertThat(rules.form(claymore, STATS).modes()).extracting(AttackForm.Mode::code).containsExactly("PLANT");
            assertThat(Cp2020AreaAttacks.areaDamage(claymore, shot("PLANT", 0, 0), sides -> 1).radius()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("Что остаётся на цели")
    class Lingering {

        @Test
        @DisplayName("Зажигательная: 4D6 ещё два раунда, по той же зоне (с. 63)")
        void incendiary() {
            AttackHit hit = Cp2020AreaAttacks.hit(blast("grenade_incendiary", "THROW"), sides -> 1);

            assertThat(hit.damage().getLocation()).isEqualTo("HEAD");
            assertThat(hit.effects()).singleElement().satisfies(effect -> {
                assertThat(effect.damage()).containsExactly("4D6", "4D6");
                assertThat(effect.rounds()).isEqualTo(2);
                AttackHit next = Cp2020AreaAttacks.effectHit("4D6", effect.data(), sides -> 2);
                assertThat(next.damage().getAmount()).isEqualTo(8);
                assertThat(next.damage().getLocation()).isEqualTo("HEAD");
            });
        }

        @Test
        @DisplayName("Огнемёт: 2D10, затем 1D10 и 1D6, свой тип урона (с. 110)")
        void flame() {
            AreaDamage blast = blast("k_a_f_253_flamethrower", "SINGLE");
            AttackHit hit = Cp2020AreaAttacks.hit(blast, sides -> 3);

            assertThat(blast.amount()).isEqualTo(2);
            assertThat(hit.damage().getDamageType()).isEqualTo("FLAME");
            assertThat(hit.effects()).singleElement().satisfies(effect -> {
                assertThat(effect.damage()).containsExactly("1D10", "1D6");
                assertThat(Cp2020AreaAttacks.effectHit("1D10", effect.data(), sides -> 4).damage().getDamageType()).isEqualTo("FLAME");
            });
        }

        @Test
        @DisplayName("Оглушающая: без урона, спасбросок с −5 (с. 63), мастеру решать нечего")
        void stun() {
            AttackHit hit = Cp2020AreaAttacks.hit(blast("grenade_stun", "THROW"), sides -> 1);

            assertThat(hit.damage()).isNull();
            assertThat(hit.save()).isEqualTo(new AttackHit.HitSave("STUN", -5));
            assertThat(hit.hasEffect()).isTrue();
        }

        @Test
        @DisplayName("Ослепляющая: 4 раунда и подсказка −3 к атаке; звуковая: 4 раунда")
        void senses() {
            assertThat(Cp2020AreaAttacks.hit(blast("grenade_dazzle", "THROW"), sides -> 1).effects()).singleElement().satisfies(effect -> {
                assertThat(effect.rounds()).isEqualTo(4);
                assertThat(effect.damage()).isEmpty();
                assertThat(effect.attackOption()).isEqualTo("blinded");
            });
            assertThat(Cp2020AreaAttacks.hit(blast("grenade_sonic", "THROW"), sides -> 1).effects()).singleElement()
                    .satisfies(effect -> assertThat(effect.rounds()).isEqualTo(4));
        }

        @Test
        @DisplayName("Газ пока ведёт мастер")
        void gas() {
            AttackHit hit = Cp2020AreaAttacks.hit(blast("grenade_gas", "THROW"), sides -> 1);

            assertThat(hit.hasEffect()).isFalse();
            assertThat(hit.steps()).anyMatch(step -> step.contains("мастер"));
        }
    }

    @Nested
    @DisplayName("Огонь и броня, оглушение с модификатором")
    class Health {

        private Cp2020ArmorStrategy armorStrategy;
        private Cp2020HealthStrategy strategy;
        private Counter health;
        private Counter armor;
        private CounterContext context;
        private int stunRoll = 10;

        private Counter counter(long id, String type) {
            Counter counter = new Counter();
            counter.setId(id);
            counter.setName(type);
            counter.setType(type);
            counter.setValue(0);
            return counter;
        }

        @BeforeEach
        void setUp() {
            armorStrategy = new Cp2020ArmorStrategy();
            strategy = new Cp2020HealthStrategy(sides -> stunRoll, armorStrategy);
            health = counter(1, Cp2020HealthStrategy.TYPE);
            strategy.initialize(health, Map.of());
            Counter stats = counter(2, Cp2020StatsStrategy.TYPE);
            new Cp2020StatsStrategy().initialize(stats, Map.of(Cp2020StatsStrategy.BODY, 8)); // BTM 3
            armor = counter(3, Cp2020ArmorStrategy.TYPE);
            armorStrategy.initialize(armor, Map.of());
            context = new CounterContext(health, List.of(health, stats, armor));
        }

        private void soft(int sp) {
            armorStrategy.updateAttributes(armor, Map.of(Cp2020ArmorStrategy.spKey(Cp2020Location.TORSO), sp));
        }

        private DamageRequest flame(int amount) {
            return DamageRequest.builder().amount(amount).location("TORSO").damageType("FLAME").autoSave(false).build();
        }

        @Test
        @DisplayName("Мягкая броня до 15 SP не защищает, но теряет 2 SP")
        void softBurns() {
            soft(14);

            HealthEvent event = strategy.applyDamage(context, flame(10));

            assertThat(health.getValue()).isEqualTo(7);
            assertThat(armorStrategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(12);
            assertThat(event.getSteps()).anyMatch(step -> step.contains("не защищает"));
        }

        @Test
        @DisplayName("Мягкая от 16 SP защищает и всё равно теряет 2 SP, даже если урон не прошёл")
        void softHolds() {
            soft(20);

            strategy.applyDamage(context, flame(10));

            assertThat(health.getValue()).isZero();
            assertThat(armorStrategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(18);
        }

        @Test
        @DisplayName("Спасбросок с −5: BODY 8 → нужно ≤ 3, уже оглушённого в себя не приводит")
        void stunSave() {
            stunRoll = 3;
            strategy.save(context, "STUN", null, -5);
            assertThat(health.getFlag(Cp2020HealthStrategy.STUNNED)).isFalse();

            stunRoll = 4;
            strategy.save(context, "STUN", null, -5);
            assertThat(health.getFlag(Cp2020HealthStrategy.STUNNED)).isTrue();

            stunRoll = 1;
            HealthEvent again = strategy.save(context, "STUN", null, -5);
            assertThat(health.getFlag(Cp2020HealthStrategy.STUNNED)).isTrue();
            assertThat(again.getSummary()).contains("всё ещё оглушён");

            strategy.save(context, "STUN", null);
            assertThat(health.getFlag(Cp2020HealthStrategy.STUNNED)).isFalse();
        }
    }
}
