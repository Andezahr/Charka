package app.charka.service.counter;

import app.charka.exception.NotFoundException;
import app.charka.dto.counter.request.CounterCreateRequestDto;
import app.charka.dto.counter.request.CounterUpdateRequestDto;
import app.charka.dto.counter.response.CounterResponseDto;
import app.charka.dto.counter.response.CounterTypeDto;
import app.charka.exception.CharacterNotFoundException;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.CounterStrategy;
import app.charka.model.counters.strategy.CounterStrategyFactory;
import app.charka.repository.CharacterRepository;
import app.charka.repository.CounterRepository;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CounterService {

    private final CounterRepository counterRepository;
    private final CharacterRepository characterRepository;
    private final CounterStrategyFactory strategyFactory;

    /**
     * Создаёт счётчик, а перед ним — недостающие обязательные счётчики
     * ({@link CounterStrategy#getRequiredCounterTypes()}), например характеристики и броню для здоровья CP2020.
     */
    @Transactional
    public CounterResponseDto createCounter(CounterCreateRequestDto requestDto) {

        Character character = characterRepository.findById(requestDto.getCharacterId())
                .orElseThrow(CharacterNotFoundException.forId(requestDto.getCharacterId()));

        CounterStrategy strategy = strategyFactory.getStrategy(requestDto.getCounterType());
        String systemCode = GameSystemRegistry.systemCodeOf(character);
        if (!strategy.isAvailableIn(systemCode)) {
            throw new IllegalArgumentException("Счётчик «" + strategy.getDisplayName()
                    + "» недоступен в кампании системы " + systemCode);
        }
        List<Counter> siblings = new ArrayList<>(counterRepository.findByCharacterId(character.getId()));
        if (strategy.isUniquePerCharacter() && hasType(siblings, strategy.getCounterType())) {
            throw new IllegalStateException("У персонажа уже есть счётчик «" + strategy.getDisplayName() + "»");
        }

        List<String> createdWith = new ArrayList<>();
        createRequired(strategy, character, siblings, requestDto.getAttributes(), createdWith);

        Counter counter = new Counter();
        counter.setName(requestDto.getName());
        counter.setValue(requestDto.getInitialValue());
        counter.setType(strategy.getCounterType());
        counter.setCharacter(character);
        strategy.initialize(counter, requestDto.getAttributes());

        Counter savedCounter = counterRepository.save(counter);
        siblings.add(savedCounter);

        CounterResponseDto dto = convertToResponseDto(savedCounter, siblings);
        dto.setCreatedWith(createdWith);
        return dto;
    }

    /**
     * Создаёт персонажу счётчики по умолчанию (обычно {@code GameSystem.getDefaultCounterTypes()}) с именами
     * по умолчанию. Типы, которые у персонажа уже есть (в том числе созданные как обязательные), пропускаются.
     *
     * @return имена созданных счётчиков
     */
    @Transactional
    public List<String> createDefaults(Long characterId, List<String> counterTypes) {
        List<String> created = new ArrayList<>();
        for (String type : counterTypes) {
            if (hasType(counterRepository.findByCharacterId(characterId), type)) {
                continue;
            }
            CounterCreateRequestDto request = new CounterCreateRequestDto();
            request.setCharacterId(characterId);
            request.setCounterType(type);
            request.setName(strategyFactory.getStrategy(type).getDefaultName());
            request.setInitialValue(0);
            CounterResponseDto dto = createCounter(request);
            created.addAll(dto.getCreatedWith());
            created.add(dto.getName());
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<CounterResponseDto> getAllCountersByCharacterId(Long characterId) {

        List<Counter> counters = counterRepository.findByCharacterId(characterId);
        return counters.stream()
                .sorted(Comparator.comparingInt(this::displayOrder)
                        .thenComparing(Counter::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(counter -> convertToResponseDto(counter, counters))
                .toList();
    }

    @Transactional
    public CounterResponseDto updateCounter(Long characterId, Long counterId, CounterUpdateRequestDto requestDto) {
        Counter counter = validateAndGetCounter(characterId, counterId);

        String currentValue = counter.getValue().toString();
        Integer inputValue = requestDto.getValue();

        String newValue = strategyFactory
                .getStrategy(counter.getType())
                .getNewValue(currentValue, inputValue);

        counter.setValue(Integer.valueOf(newValue));
        Counter savedCounter = counterRepository.save(counter);

        return convertToResponseDto(savedCounter);
    }

    @Transactional
    public CounterResponseDto setCounterValue(Long characterId, Long counterId, CounterUpdateRequestDto requestDto) {
        Counter counter = validateAndGetCounter(characterId, counterId);

        String newValue = requestDto.getValue().toString();

        counter.setValue(Integer.valueOf(newValue));
        Counter savedCounter = counterRepository.save(counter);

        return convertToResponseDto(savedCounter);
    }

    @Transactional
    public CounterResponseDto resetCounter(Long characterId, Long counterId) {
        Counter counter = validateAndGetCounter(characterId, counterId);

        strategyFactory.getStrategy(counter.getType()).reset(counter);
        Counter savedCounter = counterRepository.save(counter);

        return convertToResponseDto(savedCounter);
    }

    /**
     * Изменение attributes из формы редактирования карточки (характеристики, броня…).
     */
    @Transactional
    public CounterResponseDto updateAttributes(Long characterId, Long counterId, Map<String, Integer> attributes) {
        Counter counter = validateAndGetCounter(characterId, counterId);

        strategyFactory.getStrategy(counter.getType()).updateAttributes(counter, attributes);
        Counter savedCounter = counterRepository.save(counter);

        return convertToResponseDto(savedCounter);
    }

    /**
     * Особое действие типа счётчика (например, ремонт брони).
     *
     * @return сообщение для пользователя
     */
    @Transactional
    public String performAction(Long characterId, Long counterId, String action) {
        Counter counter = validateAndGetCounter(characterId, counterId);
        CounterContext context = new CounterContext(counter, counterRepository.findByCharacterId(characterId));

        String message = strategyFactory.getStrategy(counter.getType()).performAction(context, action);
        counterRepository.save(counter);
        return message;
    }

    /**
     * Удаляет счётчик, если от него не зависит другой счётчик персонажа.
     */
    @Transactional
    public void deleteCounter(Long characterId, Long counterId) {
        Counter counter = validateAndGetCounter(characterId, counterId);
        List<Counter> siblings = counterRepository.findByCharacterId(characterId);

        boolean otherOfSameType = siblings.stream()
                .anyMatch(c -> !c.getId().equals(counter.getId()) && counter.getType().equals(c.getType()));
        if (!otherOfSameType) {
            siblings.stream()
                    .filter(c -> !c.getId().equals(counter.getId()))
                    .filter(c -> strategyFactory.findStrategy(c.getType())
                            .map(s -> s.getRequiredCounterTypes().contains(counter.getType()))
                            .orElse(false))
                    .findFirst()
                    .ifPresent(dependent -> {
                        throw new IllegalStateException("«" + counter.getName() + "» нужен счётчику «"
                                + dependent.getName() + "» — сначала удалите его");
                    });
        }
        counterRepository.delete(counter);
    }

    @Transactional(readOnly = true)
    public CounterResponseDto getCounterById(Long characterId, Long counterId) {
        Counter counter = validateAndGetCounter(characterId, counterId);
        return convertToResponseDto(counter);
    }

    /**
     * Типы счётчиков, которые можно создать персонажу, — доступные в системе его кампании.
     */
    @Transactional(readOnly = true)
    public List<CounterTypeDto> getCounterTypes(Long characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(CharacterNotFoundException.forId(characterId));
        return strategyFactory.getStrategiesFor(GameSystemRegistry.systemCodeOf(character)).stream()
                .map(s -> new CounterTypeDto(s.getCounterType(), s.getDisplayName(),
                        s.getCreateFieldsTemplate(), s.usesInitialValue()))
                .toList();
    }

    /**
     * Валидация и получение счётчика с проверкой принадлежности персонажу
     */
    Counter validateAndGetCounter(Long characterId, Long counterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("Character ID не может быть null");
        }
        if (counterId == null) {
            throw new IllegalArgumentException("Counter ID не может быть null");
        }

        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new NotFoundException("Счётчик с ID " + counterId + " не найден"));

        if (!counter.getCharacter().getId().equals(characterId)) {
            throw new IllegalArgumentException("Счётчик " + counterId + " не принадлежит персонажу " + characterId);
        }

        return counter;
    }

    /**
     * Рекурсивно создаёт недостающие обязательные счётчики; параметры формы передаются каждому.
     */
    private void createRequired(CounterStrategy strategy, Character character, List<Counter> siblings,
                                Map<String, Integer> params, List<String> createdWith) {
        for (String requiredType : strategy.getRequiredCounterTypes()) {
            if (hasType(siblings, requiredType)) {
                continue;
            }
            CounterStrategy required = strategyFactory.getStrategy(requiredType);
            createRequired(required, character, siblings, params, createdWith);

            Counter counter = new Counter();
            counter.setName(required.getDefaultName());
            counter.setValue(0);
            counter.setType(requiredType);
            counter.setCharacter(character);
            required.initialize(counter, params);
            siblings.add(counterRepository.save(counter));
            createdWith.add(counter.getName());
        }
    }

    private static boolean hasType(List<Counter> counters, String type) {
        return counters.stream().anyMatch(c -> type.equals(c.getType()));
    }

    private int displayOrder(Counter counter) {
        return strategyFactory.findStrategy(counter.getType())
                .map(CounterStrategy::getDisplayOrder)
                .orElse(Integer.MAX_VALUE);
    }

    private CounterResponseDto convertToResponseDto(Counter counter) {
        List<Counter> siblings = counter.getCharacter() != null
                ? counterRepository.findByCharacterId(counter.getCharacter().getId())
                : List.of(counter);
        return convertToResponseDto(counter, siblings);
    }

    private CounterResponseDto convertToResponseDto(Counter counter, List<Counter> siblings) {
        CounterResponseDto dto = new CounterResponseDto();
        dto.setId(counter.getId());
        dto.setName(counter.getName());
        dto.setCounterType(counter.getType());
        dto.setValue(counter.getValue());

        strategyFactory.findStrategy(counter.getType()).ifPresent(strategy -> {
            dto.setViewTemplate(strategy.getViewTemplate());
            dto.setTypeName(strategy.getDisplayName());
            dto.setView(strategy.describe(new CounterContext(counter, siblings)));
        });

        if (counter.getCharacter() != null) {
            dto.setCharacterName(counter.getCharacter().getName());
            dto.setCharacterId(counter.getCharacter().getId());
        }

        return dto;
    }
}
