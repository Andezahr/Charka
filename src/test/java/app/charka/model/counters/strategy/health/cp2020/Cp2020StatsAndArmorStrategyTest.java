package app.charka.model.counters.strategy.health.cp2020;

import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static app.charka.model.counters.strategy.health.cp2020.Cp2020Location.TORSO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cp2020 stats and armor strategies")
class Cp2020StatsAndArmorStrategyTest {

    private static Counter counter(long id, String type) {
        Counter counter = new Counter();
        counter.setId(id);
        counter.setName(type);
        counter.setType(type);
        counter.setValue(0);
        return counter;
    }

    @Nested
    @DisplayName("Stats")
    class StatsTests {

        private final Cp2020StatsStrategy strategy = new Cp2020StatsStrategy();
        private Counter stats;

        @BeforeEach
        void setUp() {
            stats = counter(1L, Cp2020StatsStrategy.TYPE);
        }

        @Test
        @DisplayName("initialize takes known stats from the form, defaults the rest, ignores foreign keys")
        void initialize() {
            strategy.initialize(stats, Map.of("body", 9, "ref", 8, "sp_HEAD", 12));

            assertThat(stats.getAttributes())
                    .containsEntry("body", 9).containsEntry("ref", 8)
                    .containsEntry("int", Cp2020StatsStrategy.DEFAULT_STAT)
                    .doesNotContainKey("sp_HEAD");
        }

        @Test
        @DisplayName("unknown or out-of-range stats are rejected on edit")
        void validation() {
            strategy.initialize(stats, Map.of());

            assertThatThrownBy(() -> strategy.updateAttributes(stats, Map.of("strength", 5)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> strategy.updateAttributes(stats, Map.of("body", 0)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> strategy.updateAttributes(stats, Map.of("humanityLoss", -1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("all nine stats are created in rulebook order")
        void fullSet() {
            strategy.initialize(stats, Map.of());

            Cp2020StatsView view = strategy.describe(new CounterContext(stats, List.of(stats)));

            assertThat(view.getStats()).extracting(Cp2020StatsView.StatLine::getName)
                    .containsExactly("INT", "REF", "TECH", "COOL", "ATTR", "LUCK", "MA", "BODY", "EMP");
        }

        @Test
        @DisplayName("view shows REF/INT/COOL reduced by the character's wounds")
        void describeWithWounds() {
            strategy.initialize(stats, Map.of("int", 7, "ref", 8, "cool", 6, "body", 8));
            Counter health = counter(2L, Cp2020HealthStrategy.TYPE);
            health.setValue(10); // критическое

            Cp2020StatsView view = strategy.describe(new CounterContext(stats, List.of(stats, health)));

            assertThat(view.getStats()).extracting(Cp2020StatsView.StatLine::getEffective)
                    .containsExactly(4, 4, 6, 3, 6, 6, 6, 8, 6);
            assertThat(view.getStats().get(7).isReduced()).isFalse(); // BODY раны не снижают
        }

        @Test
        @DisplayName("derived values: run = MA×3, leap = run/4, carry = BODY×10, lift = BODY×40, BTM, save")
        void derived() {
            strategy.initialize(stats, Map.of("ma", 7, "body", 9));

            Cp2020StatsView view = strategy.describe(new CounterContext(stats, List.of(stats)));

            assertThat(view.getRun()).isEqualTo(21);
            assertThat(view.getLeap()).isEqualTo(5);
            assertThat(view.getCarry()).isEqualTo(90);
            assertThat(view.getLift()).isEqualTo(360);
            assertThat(view.getBtm()).isEqualTo(3);
            assertThat(view.getSave()).isEqualTo(9);
        }

        @Test
        @DisplayName("humanity = EMP×10 − loss, current EMP = humanity/10 rounded up")
        void humanity() {
            strategy.initialize(stats, Map.of("emp", 8));
            strategy.updateAttributes(stats, Map.of("humanityLoss", 25));

            Cp2020StatsView view = strategy.describe(new CounterContext(stats, List.of(stats)));

            assertThat(view.getHumanity()).isEqualTo(55);
            assertThat(view.getMaxHumanity()).isEqualTo(80);
            Cp2020StatsView.StatLine emp = view.getStats().getLast();
            assertThat(emp.getEffective()).isEqualTo(6);
            assertThat(emp.isReduced()).isTrue();
        }

        @Test
        @DisplayName("humanity never goes below zero, EMP drops to 0 (cyberpsychosis)")
        void cyberpsychosis() {
            strategy.initialize(stats, Map.of("emp", 3));
            strategy.updateAttributes(stats, Map.of("humanityLoss", 45));

            Cp2020StatsView view = strategy.describe(new CounterContext(stats, List.of(stats)));

            assertThat(view.getHumanity()).isZero();
            assertThat(view.getStats().getLast().getEffective()).isZero();
        }
    }

    @Nested
    @DisplayName("Armor")
    class ArmorTests {

        private final Cp2020ArmorStrategy strategy = new Cp2020ArmorStrategy();
        private Counter armor;

        @BeforeEach
        void setUp() {
            armor = counter(1L, Cp2020ArmorStrategy.TYPE);
            strategy.initialize(armor, Map.of());
        }

        @Test
        @DisplayName("editing SP sets both full and current SP")
        void edit() {
            strategy.updateAttributes(armor, Map.of("sp_TORSO", 14));

            assertThat(strategy.sp(armor, TORSO)).isEqualTo(14);
            assertThat(strategy.maxSp(armor, TORSO)).isEqualTo(14);
        }

        @Test
        @DisplayName("wear lowers current SP but not below 0; repair restores it")
        void wearAndRepair() {
            strategy.updateAttributes(armor, Map.of("sp_TORSO", 1));

            strategy.wear(armor, TORSO);
            strategy.wear(armor, TORSO);
            assertThat(strategy.sp(armor, TORSO)).isZero();
            assertThat(strategy.describe(new CounterContext(armor, List.of(armor))).isWorn()).isTrue();

            String message = strategy.performAction(new CounterContext(armor, List.of(armor)), "REPAIR");
            assertThat(message).contains("отремонтирована");
            assertThat(strategy.sp(armor, TORSO)).isEqualTo(1);
        }

        @Test
        @DisplayName("unknown keys, locations and actions are rejected")
        void validation() {
            assertThatThrownBy(() -> strategy.updateAttributes(armor, Map.of("body", 5)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> strategy.updateAttributes(armor, Map.of("sp_TAIL", 5)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> strategy.updateAttributes(armor, Map.of("sp_HEAD", 61)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> strategy.performAction(new CounterContext(armor, List.of(armor)), "BURN"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
