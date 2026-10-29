package app.charka.service.counter;

import app.charka.dto.counter.request.CounterCreateRequestDto;
import app.charka.dto.counter.response.CounterResponseDto;
import app.charka.dto.counter.response.CounterTypeDto;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterStrategyFactory;
import app.charka.model.counters.strategy.SimpleIntegerCounterStrategy;
import app.charka.model.counters.strategy.health.RandomDiceRoller;
import app.charka.model.counters.strategy.health.cp2020.Cp2020ArmorStrategy;
import app.charka.model.counters.strategy.health.cp2020.Cp2020HealthStrategy;
import app.charka.model.counters.strategy.health.cp2020.Cp2020StatsStrategy;
import app.charka.repository.CharacterRepository;
import app.charka.repository.CounterRepository;
import app.charka.system.cp2020.Cp2020System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CounterService — counter dependencies")
class CounterServiceTest {

    @Mock private CounterRepository counterRepository;
    @Mock private CharacterRepository characterRepository;

    private CounterService counterService;
    private Character character;

    @BeforeEach
    void setUp() {
        Cp2020ArmorStrategy armor = new Cp2020ArmorStrategy();
        CounterStrategyFactory factory = new CounterStrategyFactory(List.of(
                new SimpleIntegerCounterStrategy(), new Cp2020StatsStrategy(), armor,
                new Cp2020HealthStrategy(new RandomDiceRoller(), armor)));
        counterService = new CounterService(counterRepository, characterRepository, factory);
        Campaign campaign = new Campaign();
        campaign.setSystem(Cp2020System.CODE);
        character = new Character();
        character.setId(1L);
        character.setName("Колясик");
        character.setCampaign(campaign);
    }

    private Counter existing(long id, String type) {
        Counter counter = new Counter();
        counter.setId(id);
        counter.setName(type);
        counter.setType(type);
        counter.setValue(0);
        counter.setCharacter(character);
        return counter;
    }

    private CounterCreateRequestDto request(String type, Map<String, Integer> attributes) {
        CounterCreateRequestDto dto = new CounterCreateRequestDto();
        dto.setCharacterId(1L);
        dto.setName("Здоровье");
        dto.setCounterType(type);
        dto.setInitialValue(0);
        dto.setAttributes(attributes);
        return dto;
    }

    /** save() выдаёт id, как это сделала бы БД */
    private void stubSaveWithIds() {
        AtomicLong ids = new AtomicLong(100);
        when(counterRepository.save(any(Counter.class))).thenAnswer(inv -> {
            Counter c = inv.getArgument(0);
            c.setId(ids.incrementAndGet());
            return c;
        });
    }

    @Nested
    @DisplayName("Creating")
    class CreateTests {

        @Test
        @DisplayName("health creates missing stats and armor first, passing form stats to them")
        void createsRequired() {
            when(characterRepository.findById(1L)).thenReturn(Optional.of(character));
            when(counterRepository.findByCharacterId(1L)).thenReturn(new ArrayList<>());
            stubSaveWithIds();

            CounterResponseDto dto = counterService.createCounter(
                    request(Cp2020HealthStrategy.TYPE, Map.of("body", 9)));

            ArgumentCaptor<Counter> saved = ArgumentCaptor.forClass(Counter.class);
            verify(counterRepository, times(3)).save(saved.capture());
            assertThat(saved.getAllValues()).extracting(Counter::getType).containsExactly(
                    Cp2020StatsStrategy.TYPE, Cp2020ArmorStrategy.TYPE, Cp2020HealthStrategy.TYPE);
            assertThat(saved.getAllValues().getFirst().getAttribute("body", 0)).isEqualTo(9);
            assertThat(dto.getCreatedWith()).containsExactly("Характеристики", "Броня");
            assertThat(dto.getView()).isNotNull();
        }

        @Test
        @DisplayName("existing required counters are reused")
        void reusesExisting() {
            when(characterRepository.findById(1L)).thenReturn(Optional.of(character));
            when(counterRepository.findByCharacterId(1L)).thenReturn(new ArrayList<>(List.of(
                    existing(1L, Cp2020StatsStrategy.TYPE), existing(2L, Cp2020ArmorStrategy.TYPE))));
            stubSaveWithIds();

            CounterResponseDto dto = counterService.createCounter(request(Cp2020HealthStrategy.TYPE, Map.of()));

            verify(counterRepository, times(1)).save(any(Counter.class));
            assertThat(dto.getCreatedWith()).isEmpty();
        }

        @Test
        @DisplayName("second counter of a unique type is rejected")
        void uniqueType() {
            when(characterRepository.findById(1L)).thenReturn(Optional.of(character));
            when(counterRepository.findByCharacterId(1L))
                    .thenReturn(new ArrayList<>(List.of(existing(1L, Cp2020StatsStrategy.TYPE))));

            assertThatThrownBy(() -> counterService.createCounter(request(Cp2020StatsStrategy.TYPE, Map.of())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("уже есть");
            verify(counterRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Game system")
    class SystemTests {

        @Test
        @DisplayName("generic campaign offers only universal counter types")
        void genericTypes() {
            character.getCampaign().setSystem("GENERIC");
            when(characterRepository.findById(1L)).thenReturn(Optional.of(character));

            assertThat(counterService.getCounterTypes(1L)).extracting(CounterTypeDto::type)
                    .containsExactly("SIMPLE_INTEGER");
        }

        @Test
        @DisplayName("character without campaign is generic")
        void noCampaignIsGeneric() {
            character.setCampaign(null);
            when(characterRepository.findById(1L)).thenReturn(Optional.of(character));

            assertThat(counterService.getCounterTypes(1L)).extracting(CounterTypeDto::type)
                    .containsExactly("SIMPLE_INTEGER");
        }

        @Test
        @DisplayName("CP2020 campaign offers CP2020 and universal types")
        void cp2020Types() {
            when(characterRepository.findById(1L)).thenReturn(Optional.of(character));

            assertThat(counterService.getCounterTypes(1L)).extracting(CounterTypeDto::type)
                    .containsExactlyInAnyOrder("SIMPLE_INTEGER", Cp2020StatsStrategy.TYPE,
                            Cp2020ArmorStrategy.TYPE, Cp2020HealthStrategy.TYPE);
        }

        @Test
        @DisplayName("CP2020 counter is rejected in a generic campaign")
        void rejectsForeignType() {
            character.getCampaign().setSystem("GENERIC");
            when(characterRepository.findById(1L)).thenReturn(Optional.of(character));

            assertThatThrownBy(() -> counterService.createCounter(request(Cp2020HealthStrategy.TYPE, Map.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("недоступен");
            verify(counterRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Default counters")
    class DefaultsTests {

        @Test
        @DisplayName("creates each missing type once, skipping ones created as required")
        void createsDefaults() {
            List<Counter> stored = new ArrayList<>();
            when(characterRepository.findById(1L)).thenReturn(Optional.of(character));
            when(counterRepository.findByCharacterId(1L)).thenAnswer(inv -> new ArrayList<>(stored));
            AtomicLong ids = new AtomicLong(100);
            when(counterRepository.save(any(Counter.class))).thenAnswer(inv -> {
                Counter c = inv.getArgument(0);
                c.setId(ids.incrementAndGet());
                stored.add(c);
                return c;
            });

            List<String> created = counterService.createDefaults(1L, List.of(
                    Cp2020HealthStrategy.TYPE, Cp2020StatsStrategy.TYPE, Cp2020ArmorStrategy.TYPE));

            assertThat(stored).extracting(Counter::getType).containsExactly(
                    Cp2020StatsStrategy.TYPE, Cp2020ArmorStrategy.TYPE, Cp2020HealthStrategy.TYPE);
            assertThat(created).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Deleting")
    class DeleteTests {

        @Test
        @DisplayName("a required counter cannot be deleted while health depends on it")
        void protectsRequired() {
            Counter stats = existing(1L, Cp2020StatsStrategy.TYPE);
            Counter health = existing(3L, Cp2020HealthStrategy.TYPE);
            when(counterRepository.findById(1L)).thenReturn(Optional.of(stats));
            when(counterRepository.findByCharacterId(1L)).thenReturn(List.of(stats, health));

            assertThatThrownBy(() -> counterService.deleteCounter(1L, 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("сначала удалите");
            verify(counterRepository, never()).delete(any());
        }

        @Test
        @DisplayName("the dependent counter itself can be deleted")
        void deletesDependent() {
            Counter stats = existing(1L, Cp2020StatsStrategy.TYPE);
            Counter health = existing(3L, Cp2020HealthStrategy.TYPE);
            when(counterRepository.findById(3L)).thenReturn(Optional.of(health));
            when(counterRepository.findByCharacterId(1L)).thenReturn(List.of(stats, health));

            counterService.deleteCounter(1L, 3L);

            verify(counterRepository).delete(health);
        }
    }

    @Nested
    @DisplayName("Listing")
    class ListTests {

        @Test
        @DisplayName("counters are ordered by type: health, stats, armor, then the rest")
        void ordered() {
            when(counterRepository.findByCharacterId(1L)).thenReturn(List.of(
                    existing(1L, "SIMPLE_INTEGER"), existing(2L, Cp2020ArmorStrategy.TYPE),
                    existing(3L, Cp2020StatsStrategy.TYPE), existing(4L, Cp2020HealthStrategy.TYPE)));

            List<CounterResponseDto> list = counterService.getAllCountersByCharacterId(1L);

            assertThat(list).extracting(CounterResponseDto::getId).containsExactly(4L, 3L, 2L, 1L);
        }
    }
}
