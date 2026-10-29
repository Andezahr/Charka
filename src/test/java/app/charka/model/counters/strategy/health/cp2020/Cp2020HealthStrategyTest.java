package app.charka.model.counters.strategy.health.cp2020;

import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.model.counters.strategy.health.HealthEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static app.charka.model.counters.strategy.health.cp2020.Cp2020HealthStrategy.*;
import static app.charka.model.counters.strategy.health.cp2020.Cp2020Location.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cp2020HealthStrategy")
class Cp2020HealthStrategyTest {

    /** Кубик с заранее заданными бросками */
    static class FixedDice implements DiceRoller {
        private final Deque<Integer> rolls = new ArrayDeque<>();

        FixedDice next(int... values) {
            for (int v : values) rolls.add(v);
            return this;
        }

        @Override
        public int roll(int sides) {
            if (rolls.isEmpty()) throw new IllegalStateException("Бросков не осталось");
            return rolls.poll();
        }
    }

    private FixedDice dice;
    private Cp2020ArmorStrategy armorStrategy;
    private Cp2020HealthStrategy strategy;
    private Counter health;
    private Counter armor;
    private CounterContext context;

    @BeforeEach
    void setUp() {
        dice = new FixedDice();
        armorStrategy = new Cp2020ArmorStrategy();
        strategy = new Cp2020HealthStrategy(dice, armorStrategy);

        health = counter(1L, "Колясик", TYPE);
        strategy.initialize(health, Map.of());
        Counter stats = counter(2L, "Характеристики", Cp2020StatsStrategy.TYPE);
        new Cp2020StatsStrategy().initialize(stats, Map.of(Cp2020StatsStrategy.BODY, 8)); // BTM 3
        armor = counter(3L, "Броня", Cp2020ArmorStrategy.TYPE);
        armorStrategy.initialize(armor, Map.of());

        context = new CounterContext(health, List.of(health, stats, armor));
    }

    private static Counter counter(long id, String name, String type) {
        Counter counter = new Counter();
        counter.setId(id);
        counter.setName(name);
        counter.setType(type);
        counter.setValue(0);
        return counter;
    }

    private void wearArmor(Cp2020Location location, int sp) {
        armorStrategy.updateAttributes(armor, Map.of(Cp2020ArmorStrategy.spKey(location), sp));
    }

    private DamageRequest hit(int amount, String location) {
        return DamageRequest.builder().amount(amount).location(location).autoSave(false).build();
    }

    private DamageRequest hit(int amount, String location, String type) {
        return DamageRequest.builder().amount(amount).location(location).damageType(type).autoSave(false).build();
    }

    @Nested
    @DisplayName("Rules tables")
    class RulesTests {

        @ParameterizedTest(name = "BODY {0} → BTM {1}")
        @CsvSource({"2,0", "3,1", "4,1", "5,2", "7,2", "8,3", "9,3", "10,4", "11,5", "15,5"})
        void btm(int body, int expected) {
            assertThat(Cp2020Rules.btm(body)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "damage {0} → level {1}")
        @CsvSource({"0,-1", "1,0", "4,0", "5,1", "8,1", "9,2", "12,2", "13,3", "40,9", "45,9"})
        void woundLevel(int damage, int expected) {
            assertThat(Cp2020Rules.woundLevel(damage)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "d10 {0} → {1}")
        @CsvSource({"1,HEAD", "2,TORSO", "4,TORSO", "5,R_ARM", "6,L_ARM", "7,R_LEG", "8,R_LEG", "9,L_LEG", "10,L_LEG"})
        void hitLocation(int roll, Cp2020Location expected) {
            assertThat(Cp2020Location.fromRoll(roll)).isEqualTo(expected);
        }

        @Test
        @DisplayName("wound penalties: serious REF −2, critical ÷2, mortal ÷3, rounded up")
        void penalties() {
            assertThat(Cp2020Rules.effectiveRef(9, 0)).isEqualTo(9);
            assertThat(Cp2020Rules.effectiveRef(9, 1)).isEqualTo(7);
            assertThat(Cp2020Rules.effectiveIntOrCool(9, 1)).isEqualTo(9);
            assertThat(Cp2020Rules.effectiveRef(9, 2)).isEqualTo(5);
            assertThat(Cp2020Rules.effectiveIntOrCool(7, 3)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Applying damage")
    class DamageTests {

        @Test
        @DisplayName("armor stops damage that does not exceed SP and is not worn")
        void armorStopsDamage() {
            wearArmor(TORSO, 14);

            HealthEvent event = strategy.applyDamage(context, hit(12, "TORSO"));

            assertThat(health.getValue()).isZero();
            assertThat(armorStrategy.sp(armor, TORSO)).isEqualTo(14);
            assertThat(event.getSummary()).contains("броня выдержала");
        }

        @Test
        @DisplayName("damage = raw − SP − BTM, the armor counter is worn by 1")
        void armorAndBtm() {
            wearArmor(TORSO, 10);

            strategy.applyDamage(context, hit(18, "TORSO"));

            assertThat(health.getValue()).isEqualTo(5); // 18 − 10 − 3
            assertThat(armorStrategy.sp(armor, TORSO)).isEqualTo(9);
            assertThat(armorStrategy.maxSp(armor, TORSO)).isEqualTo(10);
        }

        @Test
        @DisplayName("BTM never reduces a penetrating hit below 1")
        void minimumOne() {
            strategy.applyDamage(context, hit(2, "TORSO"));

            assertThat(health.getValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("head hits are doubled after armor and before BTM")
        void headDoubled() {
            wearArmor(HEAD, 10);

            strategy.applyDamage(context, hit(13, "HEAD"));

            assertThat(health.getValue()).isEqualTo(3); // (13 − 10) × 2 − 3
        }

        @Test
        @DisplayName("more than 8 damage to the head kills instantly")
        void headshotKills() {
            strategy.applyDamage(context, hit(10, "HEAD")); // 10 × 2 − 3 = 17

            assertThat(strategy.describe(context).isDead()).isTrue();
            assertThatThrownBy(() -> strategy.applyDamage(context, hit(1, "TORSO")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("more than 8 damage to a limb severs it and requires a death save")
        void limbSevered() {
            strategy.applyDamage(context, hit(12, "L_ARM")); // 12 − 3 = 9

            Cp2020HealthView view = strategy.describe(context);
            assertThat(view.getLostLimbs()).extracting(Cp2020HealthView.Option::getCode).containsExactly("L_ARM");
            assertThat(view.isDeathSaveRequired()).isTrue();
            assertThat(view.getDeathTarget()).isEqualTo(8); // BODY − Смертельное 0
        }

        @Test
        @DisplayName("armor-piercing halves SP and the damage that gets through")
        void armorPiercing() {
            wearArmor(TORSO, 20);

            strategy.applyDamage(context, hit(24, "TORSO", "ARMOR_PIERCING"));

            assertThat(health.getValue()).isEqualTo(4); // (24 − 10) / 2 − 3
            assertThat(armorStrategy.sp(armor, TORSO)).isEqualTo(19);
        }

        @Test
        @DisplayName("bypass damage ignores and does not wear armor")
        void bypass() {
            wearArmor(TORSO, 20);

            strategy.applyDamage(context, hit(10, "TORSO", "BYPASS"));

            assertThat(health.getValue()).isEqualTo(7);
            assertThat(armorStrategy.sp(armor, TORSO)).isEqualTo(20);
        }

        @Test
        @DisplayName("random location is rolled when none is given")
        void randomLocation() {
            dice.next(1); // голова
            wearArmor(HEAD, 5);

            HealthEvent event = strategy.applyDamage(context, hit(7, null));

            assertThat(health.getValue()).isEqualTo(1); // (7 − 5) × 2 − 3
            assertThat(event.getSteps().getFirst()).contains("d10 = 1").contains("Голова");
        }

        @Test
        @DisplayName("damage beyond 40 kills")
        void overMaxKills() {
            health.setValue(38);

            strategy.applyDamage(context, hit(8, "TORSO"));

            assertThat(strategy.describe(context).isDead()).isTrue();
        }

        @Test
        @DisplayName("auto stun save is rolled after the hit; failure stuns")
        void autoStunSave() {
            dice.next(9); // BODY 8 − 1 (серьёзное) = 7 → провал
            DamageRequest request = DamageRequest.builder().amount(9).location("TORSO").build();

            HealthEvent event = strategy.applyDamage(context, request); // 6 урона — серьёзное

            assertThat(health.getFlag(STUNNED)).isTrue();
            assertThat(event.getSteps()).anyMatch(s -> s.contains("нужно ≤ 7"));
        }

        @Test
        @DisplayName("damage without an armor counter fails with a clear message")
        void missingArmor() {
            CounterContext alone = new CounterContext(health, List.of(health));

            assertThatThrownBy(() -> strategy.applyDamage(alone, hit(5, "TORSO")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Броня");
        }
    }

    @Nested
    @DisplayName("Saves, stabilization and healing")
    class SaveTests {

        @Test
        @DisplayName("stun save success recovers a stunned character")
        void stunRecovery() {
            health.setFlag(STUNNED, true);

            strategy.save(context, SAVE_STUN, 8);

            assertThat(health.getFlag(STUNNED)).isFalse();
        }

        @Test
        @DisplayName("death save target is BODY minus mortal level; failure kills")
        void deathSave() {
            health.setValue(22); // Смертельное 2

            assertThat(strategy.describe(context).getDeathTarget()).isEqualTo(6);
            strategy.save(context, SAVE_DEATH, 7);

            assertThat(strategy.describe(context).isDead()).isTrue();
        }

        @Test
        @DisplayName("death save is rejected when not mortally wounded")
        void deathSaveNotNeeded() {
            health.setValue(5);

            assertThatThrownBy(() -> strategy.save(context, SAVE_DEATH, 1))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("stabilized characters no longer need death saves, new damage removes stabilization")
        void stabilize() {
            health.setValue(14);

            strategy.stabilize(context);
            assertThat(strategy.describe(context).isDeathSaveRequired()).isFalse();

            strategy.applyDamage(context, hit(4, "TORSO"));
            assertThat(strategy.describe(context).isDeathSaveRequired()).isTrue();
        }

        @Test
        @DisplayName("roll outside 1–10 is rejected")
        void badRoll() {
            assertThatThrownBy(() -> strategy.save(context, SAVE_STUN, 11))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("heal does not go below zero")
        void heal() {
            health.setValue(3);

            strategy.heal(context, 10);

            assertThat(health.getValue()).isZero();
        }

        @Test
        @DisplayName("natural healing skips the dying without stabilization")
        void healOverTime() {
            health.setValue(20);
            strategy.healOverTime(context, 5);
            assertThat(health.getValue()).isEqualTo(20);

            health.setFlag(STABILIZED, true);
            strategy.healOverTime(context, 5);
            assertThat(health.getValue()).isEqualTo(15);
        }

        @Test
        @DisplayName("reset heals fully and clears states and lost limbs")
        void reset() {
            health.setValue(41);
            health.setFlag(DEAD, true);
            strategy.updateAttributes(health, Map.of("lost_R_LEG", 1));

            strategy.reset(health);

            assertThat(health.getValue()).isZero();
            assertThat(strategy.describe(context).isDead()).isFalse();
            assertThat(strategy.describe(context).getLostLimbs()).isEmpty();
        }
    }

    @Nested
    @DisplayName("View")
    class ViewTests {

        @Test
        @DisplayName("view shows track, status, save targets and penalty")
        void describe() {
            health.setValue(10); // критическое

            Cp2020HealthView view = strategy.describe(context);

            assertThat(view.getStatusLabel()).isEqualTo("Критическое ранение");
            assertThat(view.getStatusCss()).isEqualTo("critical");
            assertThat(view.getTrack()).extracting(Cp2020HealthView.TrackLevel::getFilled)
                    .containsExactly(4, 4, 2, 0, 0, 0, 0, 0, 0, 0);
            assertThat(view.getStunTarget()).isEqualTo(6);
            assertThat(view.getPenalty()).isEqualTo("REF, INT, COOL ÷2");
        }

        @Test
        @DisplayName("view falls back to default BODY when there is no stats counter")
        void describeWithoutStats() {
            Cp2020HealthView view = strategy.describe(new CounterContext(health, List.of(health)));

            assertThat(view.getBody()).isEqualTo(Cp2020StatsStrategy.DEFAULT_STAT);
        }

        @Test
        @DisplayName("only lost-limb flags can be edited")
        void attributesValidation() {
            assertThatThrownBy(() -> strategy.updateAttributes(health, Map.of("body", 5)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
