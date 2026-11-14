package app.charka.system.kosmozeki;

import app.charka.model.counters.Counter;
import app.charka.counter.CounterContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KZT-01: способности Космозэков без Spring — «Сложность вдвое» (раз за бой) и «Предсказание» (3к12 после сна).
 */
@DisplayName("Космозэки: способности")
class KzAbilityStrategiesTest {

    private static Counter counter(String type) {
        Counter counter = new Counter();
        counter.setType(type);
        return counter;
    }

    private static CounterContext context(Counter counter) {
        return new CounterContext(counter, List.of(counter));
    }

    @Nested
    @DisplayName("Сложность вдвое")
    class Halve {

        private final KzHalveStrategy strategy = new KzHalveStrategy();

        @Test
        @DisplayName("Новая способность — один раз за бой")
        void startsWithOne() {
            Counter counter = counter(KzHalveStrategy.TYPE);
            strategy.initialize(counter, Map.of());
            assertThat(counter.getValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("Новый бой возвращает столько, сколько за бой (max)")
        void resetToMax() {
            Counter counter = counter(KzHalveStrategy.TYPE);
            strategy.updateAttributes(counter, Map.of("max", 2));
            counter.setValue(0);
            strategy.reset(counter);
            assertThat(counter.getValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("Раз за бой меньше 1 — отказ")
        void maxAtLeastOne() {
            assertThatThrownBy(() -> strategy.updateAttributes(counter(KzHalveStrategy.TYPE), Map.of("max", 0)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Значение не уходит ниже нуля")
        void notBelowZero() {
            assertThat(strategy.getNewValue("0", -1)).isEqualTo("0");
        }

        @Test
        @DisplayName("RESTORE возвращает способность")
        void restore() {
            Counter counter = counter(KzHalveStrategy.TYPE);
            counter.setValue(0);
            strategy.performAction(context(counter), "RESTORE");
            assertThat(counter.getValue()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Предсказание")
    class Portent {

        private int next = 0;
        private final KzPortentStrategy strategy = new KzPortentStrategy(sides -> {
            assertThat(sides).isEqualTo(12);
            return ++next;
        });

        private Counter rolled() {
            Counter counter = counter(KzPortentStrategy.TYPE);
            strategy.initialize(counter, Map.of());
            return counter;
        }

        @Test
        @DisplayName("Сон бросает 3к12")
        void threeD12() {
            Counter counter = rolled();
            assertThat(KzPortentStrategy.dice(counter)).containsExactly(Map.entry(1, 1), Map.entry(2, 2), Map.entry(3, 3));
            assertThat(counter.getValue()).isEqualTo(3);
        }

        @Test
        @DisplayName("Отложенный кубик заменяет следующий к12 один раз")
        void useOnce() {
            Counter counter = rolled();
            strategy.performAction(context(counter), "USE_2");
            assertThat(counter.getValue()).isEqualTo(2);
            assertThat(KzPortentStrategy.useNext(counter)).isEqualTo(2);
            assertThat(KzPortentStrategy.useNext(counter)).isZero();
            assertThatThrownBy(() -> strategy.performAction(context(counter), "USE_2"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Отложить можно только один кубик")
        void oneAtATime() {
            Counter counter = rolled();
            strategy.performAction(context(counter), "USE_1");
            assertThatThrownBy(() -> strategy.performAction(context(counter), "USE_3"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CANCEL возвращает отложенный кубик")
        void cancel() {
            Counter counter = rolled();
            strategy.performAction(context(counter), "USE_1");
            strategy.performAction(context(counter), "CANCEL");
            assertThat(counter.getValue()).isEqualTo(3);
            assertThat(KzPortentStrategy.dice(counter).values()).containsExactlyInAnyOrder(1, 2, 3);
        }

        @Test
        @DisplayName("Новый сон заменяет оставшиеся и отложенный")
        void rerollReplaces() {
            Counter counter = rolled();
            strategy.performAction(context(counter), "USE_1");
            strategy.performAction(context(counter), "ROLL");
            assertThat(KzPortentStrategy.dice(counter).values()).containsExactly(4, 5, 6);
            assertThat(KzPortentStrategy.useNext(counter)).isZero();
        }
    }
}
