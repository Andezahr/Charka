package app.charka.system.kosmozeki;

import app.charka.model.counters.Counter;
import app.charka.counter.CounterContext;
import app.charka.counter.health.DamageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KZT-01: тело Космозэков без Spring — ХП по зонам, броня из каждого попадания, множитель головы,
 * без сознания на нуле, отдых (RULES_KOSMOZEKI.md; хоумбрю — «Домашние правила» в TODO.md).
 */
@DisplayName("Космозэки: тело")
class KzBodyStrategyTest {

    private int nextD10 = 5;
    private final KzBodyStrategy strategy = new KzBodyStrategy(sides -> nextD10);
    private Counter counter;

    @BeforeEach
    void setUp() {
        counter = new Counter();
        counter.setType(KzBodyStrategy.TYPE);
        strategy.initialize(counter, Map.of());
    }

    private CounterContext context() {
        return new CounterContext(counter, List.of(counter));
    }

    private void damage(int amount, String zone, Map<String, Integer> extras) {
        strategy.applyDamage(context(), DamageRequest.builder().amount(amount).location(zone).extras(extras).build());
    }

    private int hp(String zone) {
        return KzBodyStrategy.hp(counter, KzBodyStrategy.body(counter).zone(zone).orElseThrow());
    }

    @Nested
    @DisplayName("Урон")
    class Damage {

        @Test
        @DisplayName("Человек: у тела 150 ХП, у остальных зон по 100")
        void humanZones() {
            assertThat(hp(KzBody.BODY)).isEqualTo(150);
            assertThat(hp(KzBody.HEAD)).isEqualTo(100);
            assertThat(counter.getValue()).isZero();
        }

        @Test
        @DisplayName("Урон уходит в выбранную зону и в общий счёт потерь")
        void toZone() {
            damage(30, KzBody.RIGHT_ARM, Map.of());
            assertThat(hp(KzBody.RIGHT_ARM)).isEqualTo(70);
            assertThat(counter.getValue()).isEqualTo(30);
        }

        @Test
        @DisplayName("Голова — урон ×2")
        void headDouble() {
            damage(30, KzBody.HEAD, Map.of());
            assertThat(hp(KzBody.HEAD)).isEqualTo(40);
        }

        @Test
        @DisplayName("Броня вычитается из каждого попадания")
        void armorPerHit() {
            strategy.updateAttributes(counter, Map.of(KzBodyStrategy.ARMOR_PREFIX + KzBody.BODY, 10));
            damage(25, KzBody.BODY, Map.of(KzBodyStrategy.HITS, 3));
            assertThat(hp(KzBody.BODY)).isEqualTo(150 - 15 * 3);
        }

        @Test
        @DisplayName("Ближнее оружие: броня вдвое меньше, вниз")
        void halfArmor() {
            strategy.updateAttributes(counter, Map.of(KzBodyStrategy.ARMOR_PREFIX + KzBody.BODY, 15));
            damage(20, KzBody.BODY, Map.of(KzBodyStrategy.ARMOR_HALF, 1));
            assertThat(hp(KzBody.BODY)).isEqualTo(150 - 13);
        }

        @Test
        @DisplayName("Пример правил: 3 пули по 20 в голову, броня 15 → 30")
        void rulesExample() {
            strategy.updateAttributes(counter, Map.of(KzBodyStrategy.ARMOR_PREFIX + KzBody.HEAD, 15));
            damage(20, KzBody.HEAD, Map.of(KzBodyStrategy.HITS, 3));
            assertThat(hp(KzBody.HEAD)).isEqualTo(70);
        }

        @Test
        @DisplayName("Броня больше урона — урона нет")
        void armorHolds() {
            strategy.updateAttributes(counter, Map.of(KzBodyStrategy.ARMOR_PREFIX + KzBody.BODY, 30));
            damage(20, KzBody.BODY, Map.of());
            assertThat(hp(KzBody.BODY)).isEqualTo(150);
        }

        @Test
        @DisplayName("Бонус крита — после брони и множителя")
        void critBonus() {
            strategy.updateAttributes(counter, Map.of(KzBodyStrategy.ARMOR_PREFIX + KzBody.HEAD, 10));
            damage(20, KzBody.HEAD, Map.of(KzBodyStrategy.BONUS, 5));
            assertThat(hp(KzBody.HEAD)).isEqualTo(100 - (20 - 10) * 2 - 5);
        }

        @Test
        @DisplayName("Зона без выбора — по последней цифре броска")
        void byDigit() {
            damage(10, null, Map.of(KzBodyStrategy.DIGIT, 42));
            assertThat(hp(KzBody.RIGHT_ARM)).isEqualTo(100);
            assertThat(KzBodyStrategy.hp(counter, KzBodyStrategy.body(counter).byDigit(2).orElseThrow())).isEqualTo(90);
        }

        @Test
        @DisplayName("Без цифры — бросок к10")
        void byD10() {
            nextD10 = 10;
            damage(10, null, Map.of());
            assertThat(hp(KzBody.HEAD)).isEqualTo(80);
        }
    }

    @Nested
    @DisplayName("Без сознания")
    class Unconscious {

        @Test
        @DisplayName("Зона на нуле — без сознания; лишний урон пропадает")
        void zoneDown() {
            damage(200, KzBody.RIGHT_ARM, Map.of());
            assertThat(hp(KzBody.RIGHT_ARM)).isZero();
            assertThat(counter.getValue()).isEqualTo(100);
            assertThat(KzBodyStrategy.unconscious(counter)).isTrue();
            damage(50, KzBody.RIGHT_ARM, Map.of());
            assertThat(counter.getValue()).isEqualTo(100);
        }

        @Test
        @DisplayName("Мастер поднял — в сознании, пока новая зона не упадёт")
        void awakeUntilNextZone() {
            damage(100, KzBody.RIGHT_ARM, Map.of());
            strategy.performAction(context(), "AWAKE");
            assertThat(KzBodyStrategy.unconscious(counter)).isFalse();
            damage(100, KzBody.LEFT_ARM, Map.of());
            assertThat(KzBodyStrategy.unconscious(counter)).isTrue();
        }
    }

    @Nested
    @DisplayName("Отдых")
    class Rest {

        @Test
        @DisplayName("По умолчанию сон и еда: +60 в каждую зону за день")
        void fedByDefault() {
            damage(100, KzBody.BODY, Map.of());
            damage(50, KzBody.LEFT_LEG, Map.of());
            strategy.healOverTime(context(), 1);
            assertThat(hp(KzBody.BODY)).isEqualTo(110);
            assertThat(hp(KzBody.LEFT_LEG)).isEqualTo(100);
        }

        @Test
        @DisplayName("Только сон: +30 в день")
        void sleepOnly() {
            strategy.setRest(counter, "1");
            damage(100, KzBody.BODY, Map.of());
            strategy.healOverTime(context(), 2);
            assertThat(hp(KzBody.BODY)).isEqualTo(110);
        }

        @Test
        @DisplayName("Без отдыха и мёртвый не лечатся")
        void noRest() {
            damage(100, KzBody.BODY, Map.of());
            strategy.setRest(counter, "0");
            strategy.healOverTime(context(), 5);
            assertThat(hp(KzBody.BODY)).isEqualTo(50);
            strategy.setRest(counter, "2");
            strategy.performAction(context(), "DEAD");
            strategy.healOverTime(context(), 5);
            assertThat(hp(KzBody.BODY)).isEqualTo(50);
        }
    }
}
