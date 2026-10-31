package app.charka.service.counter;

import app.charka.dto.counter.request.HealthDamageRequestDto;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.CounterStrategy;
import app.charka.model.counters.strategy.CounterStrategyFactory;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.HealthCounterStrategy;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.model.counters.strategy.health.HealthView;
import app.charka.repository.CounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Операции со счётчиками здоровья. Ничего не знает о конкретной игровой системе —
 * всё делегируется {@link HealthCounterStrategy}, найденной по типу счётчика.
 */
@Service
@RequiredArgsConstructor
public class HealthCounterService {

    private final CounterService counterService;
    private final CounterRepository counterRepository;
    private final CounterStrategyFactory strategyFactory;

    @Transactional
    public HealthEvent applyDamage(Long characterId, Long counterId, HealthDamageRequestDto requestDto) {
        CounterContext context = contextFor(characterId, counterId);
        DamageRequest request = DamageRequest.builder()
                .amount(requestDto.getAmount())
                .location(requestDto.getLocation())
                .damageType(requestDto.getDamageType())
                .autoSave(requestDto.isAutoSave())
                .build();
        HealthEvent event = strategyFor(context).applyDamage(context, request);
        // урон изнашивает броню — сохраняем и соседние счётчики
        counterRepository.saveAll(context.getSiblings());
        return event;
    }

    /**
     * Урон персонажу без выбора счётчика: например, от атаки.
     *
     * @return итог; пусто, если у персонажа нет счётчика здоровья
     */
    @Transactional
    public Optional<HealthEvent> applyDamage(Long characterId, DamageRequest request) {
        List<Counter> siblings = counterRepository.findByCharacterId(characterId);
        for (Counter counter : siblings) {
            if (strategyFactory.findStrategy(counter.getType()).orElse(null) instanceof HealthCounterStrategy health) {
                CounterContext context = new CounterContext(counter, siblings);
                if (health.isDead(context)) {
                    return Optional.of(new HealthEvent().step("Цель уже мертва").summary("Цель уже мертва"));
                }
                HealthEvent event = health.applyDamage(context, request);
                counterRepository.saveAll(siblings);
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    /**
     * Спасбросок персонажа от попадания, без выбора счётчика.
     *
     * @param modifier прибавка к числу спасброска
     * @return итог; пусто, если у персонажа нет счётчика здоровья
     */
    @Transactional
    public Optional<HealthEvent> save(Long characterId, String saveType, int modifier) {
        List<Counter> siblings = counterRepository.findByCharacterId(characterId);
        for (Counter counter : siblings) {
            if (strategyFactory.findStrategy(counter.getType()).orElse(null) instanceof HealthCounterStrategy health) {
                CounterContext context = new CounterContext(counter, siblings);
                if (health.isDead(context)) {
                    return Optional.of(new HealthEvent().step("Цель уже мертва").summary("Цель уже мертва"));
                }
                HealthEvent event = health.save(context, saveType, null, modifier);
                counterRepository.saveAll(siblings);
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    /**
     * Состояние счётчика здоровья для показа.
     *
     * @param siblings все счётчики персонажа
     * @throws IllegalArgumentException если это не счётчик здоровья
     */
    public HealthView describe(Counter counter, List<Counter> siblings) {
        return strategyFor(new CounterContext(counter, siblings)).describe(new CounterContext(counter, siblings));
    }

    @Transactional
    public HealthEvent heal(Long characterId, Long counterId, int points) {
        CounterContext context = contextFor(characterId, counterId);
        HealthEvent event = strategyFor(context).heal(context, points);
        counterRepository.save(context.getCounter());
        return event;
    }

    /**
     * @param roll бросок игрока; null — бросает сервер
     */
    @Transactional
    public HealthEvent save(Long characterId, Long counterId, String saveType, Integer roll) {
        CounterContext context = contextFor(characterId, counterId);
        HealthEvent event = strategyFor(context).save(context, saveType, roll);
        counterRepository.save(context.getCounter());
        return event;
    }

    @Transactional
    public HealthEvent stabilize(Long characterId, Long counterId) {
        CounterContext context = contextFor(characterId, counterId);
        HealthEvent event = strategyFor(context).stabilize(context);
        counterRepository.save(context.getCounter());
        return event;
    }

    /**
     * Естественное восстановление всех персонажей кампании за прошедшие дни.
     *
     * @return итоги по каждому счётчику, где что-то произошло
     */
    @Transactional
    public List<String> healOverTime(Long campaignId, int days) {
        List<String> results = new ArrayList<>();
        Map<Long, List<Counter>> byCharacter = counterRepository.findByCharacterCampaignId(campaignId).stream()
                .collect(Collectors.groupingBy(c -> c.getCharacter().getId()));
        for (List<Counter> siblings : byCharacter.values()) {
            for (Counter counter : siblings) {
                CounterStrategy strategy = strategyFactory.findStrategy(counter.getType()).orElse(null);
                if (strategy instanceof HealthCounterStrategy health) {
                    HealthEvent event = health.healOverTime(new CounterContext(counter, siblings), days);
                    if (event.getSummary() != null) {
                        results.add(event.getSummary());
                    }
                    counterRepository.save(counter);
                }
            }
        }
        return results;
    }

    private CounterContext contextFor(Long characterId, Long counterId) {
        Counter counter = counterService.validateAndGetCounter(characterId, counterId);
        return new CounterContext(counter, counterRepository.findByCharacterId(characterId));
    }

    private HealthCounterStrategy strategyFor(CounterContext context) {
        Counter counter = context.getCounter();
        CounterStrategy strategy = strategyFactory.getStrategy(counter.getType());
        if (strategy instanceof HealthCounterStrategy health) {
            return health;
        }
        throw new IllegalArgumentException("Счётчик '" + counter.getName() + "' не является счётчиком здоровья");
    }
}
