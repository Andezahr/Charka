package app.charka.service.counter;

import app.charka.dto.counter.request.HealthDamageRequestDto;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterStrategyFactory;
import app.charka.model.counters.strategy.SimpleIntegerCounterStrategy;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.model.counters.strategy.health.RandomDiceRoller;
import app.charka.system.cp2020.Cp2020ArmorStrategy;
import app.charka.system.cp2020.Cp2020HealthStrategy;
import app.charka.system.cp2020.Cp2020Location;
import app.charka.system.cp2020.Cp2020StatsStrategy;
import app.charka.repository.CounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthCounterService")
class HealthCounterServiceTest {

    @Mock private CounterService counterService;
    @Mock private CounterRepository counterRepository;

    private HealthCounterService healthCounterService;
    private Cp2020ArmorStrategy armorStrategy;
    private Cp2020HealthStrategy healthStrategy;
    private Cp2020StatsStrategy statsStrategy;
    private Character character;

    @BeforeEach
    void setUp() {
        armorStrategy = new Cp2020ArmorStrategy();
        statsStrategy = new Cp2020StatsStrategy();
        healthStrategy = new Cp2020HealthStrategy(new RandomDiceRoller(), armorStrategy);
        CounterStrategyFactory factory = new CounterStrategyFactory(
                List.of(new SimpleIntegerCounterStrategy(), healthStrategy, statsStrategy, armorStrategy));
        healthCounterService = new HealthCounterService(counterService, counterRepository, factory);
        character = new Character();
        character.setId(1L);
    }

    private Counter counter(long id, String type) {
        Counter counter = new Counter();
        counter.setId(id);
        counter.setName(type);
        counter.setType(type);
        counter.setValue(0);
        counter.setCharacter(character);
        return counter;
    }

    /** Здоровье + характеристики (BODY 6, BTM 2) + броня */
    private List<Counter> cp2020Set() {
        Counter health = counter(5L, Cp2020HealthStrategy.TYPE);
        Counter stats = counter(6L, Cp2020StatsStrategy.TYPE);
        statsStrategy.initialize(stats, Map.of());
        Counter armor = counter(7L, Cp2020ArmorStrategy.TYPE);
        armorStrategy.initialize(armor, Map.of());
        return List.of(health, stats, armor);
    }

    @Nested
    @DisplayName("Damage")
    class DamageTests {

        @Test
        @DisplayName("should apply damage using sibling counters and save the worn armor too")
        void shouldApplyAndSaveSiblings() {
            List<Counter> set = cp2020Set();
            Counter health = set.get(0);
            Counter armor = set.get(2);
            armorStrategy.updateAttributes(armor, Map.of("sp_TORSO", 3));
            when(counterService.validateAndGetCounter(1L, 5L)).thenReturn(health);
            when(counterRepository.findByCharacterId(1L)).thenReturn(set);
            HealthDamageRequestDto dto = new HealthDamageRequestDto();
            dto.setAmount(10);
            dto.setLocation("TORSO");
            dto.setAutoSave(false);

            HealthEvent event = healthCounterService.applyDamage(1L, 5L, dto);

            assertThat(health.getValue()).isEqualTo(5); // 10 − SP 3 − BTM 2
            assertThat(armorStrategy.sp(armor, Cp2020Location.TORSO)).isEqualTo(2);
            assertThat(event.getSteps()).isNotEmpty();
            verify(counterRepository).saveAll(set);
        }

        @Test
        @DisplayName("should reject counters that are not health counters")
        void shouldRejectSimpleCounter() {
            Counter ammo = counter(8L, "SIMPLE_INTEGER");
            when(counterService.validateAndGetCounter(1L, 8L)).thenReturn(ammo);
            when(counterRepository.findByCharacterId(1L)).thenReturn(List.of(ammo));

            assertThatThrownBy(() -> healthCounterService.heal(1L, 8L, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не является счётчиком здоровья");
            verify(counterRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Natural healing over campaign time")
    class HealOverTimeTests {

        @Test
        @DisplayName("should heal only health counters of the campaign")
        void shouldHealHealthCountersOnly() {
            List<Counter> set = cp2020Set();
            Counter wounded = set.get(0);
            wounded.setValue(6);
            Counter ammo = counter(9L, "SIMPLE_INTEGER");
            ammo.setValue(30);
            when(counterRepository.findByCharacterCampaignId(9L))
                    .thenReturn(List.of(wounded, set.get(1), set.get(2), ammo));

            List<String> results = healthCounterService.healOverTime(9L, 2);

            assertThat(wounded.getValue()).isEqualTo(4);
            assertThat(ammo.getValue()).isEqualTo(30);
            assertThat(results).hasSize(1);
            verify(counterRepository).save(wounded);
            verify(counterRepository, never()).save(ammo);
        }
    }
}
