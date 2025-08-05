package app.charka.service.counter;

import app.charka.dto.counter.request.CounterCreateRequestDto;
import app.charka.dto.counter.request.CounterUpdateRequestDto;
import app.charka.dto.counter.response.CounterResponseDto;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterStrategyFactory;
import app.charka.repository.CharacterRepository;
import app.charka.repository.CounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CounterService {

    private final CounterRepository counterRepository;
    private final CharacterRepository characterRepository;
    private final CounterStrategyFactory strategyFactory;

    /**
     * Создать новый счётчик для персонажа
     */
    @Transactional
    public CounterResponseDto createCounter(CounterCreateRequestDto requestDto) {
        // Валидация обязательных полей
        if (requestDto.getCharacterId() == null) {
            throw new IllegalArgumentException("Character ID не может быть null");
        }

        Character character = characterRepository.findById(requestDto.getCharacterId())
                .orElseThrow(() -> new IllegalArgumentException("Персонаж с ID " + requestDto.getCharacterId() + " не найден"));

        Counter counter = new Counter();
        counter.setName(requestDto.getName());
        counter.setValue(requestDto.getInitialValue());
        counter.setType(requestDto.getCounterType()); // Используем тип из DTO
        counter.setCharacter(character);

        Counter savedCounter = counterRepository.save(counter);
        return convertToResponseDto(savedCounter);
    }

    /**
     * Получить все счётчики персонажа
     */
    @Transactional(readOnly = true)
    public List<CounterResponseDto> getAllCountersByCharacterId(Long characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("Character ID не может быть null");
        }

        // Проверяем, что персонаж существует
        if (!characterRepository.existsById(characterId)) {
            throw new IllegalArgumentException("Персонаж с ID " + characterId + " не найден");
        }

        List<Counter> counters = counterRepository.findByCharacterId(characterId);
        return counters.stream()
                .map(this::convertToResponseDto)
                .toList();
    }

    /**
     * Увеличить счётчик
     */
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

    /**
     * Установить значение счётчика
     */
    @Transactional
    public CounterResponseDto setCounterValue(Long characterId, Long counterId, CounterUpdateRequestDto requestDto) {
        Counter counter = validateAndGetCounter(characterId, counterId);

        String newValue = requestDto.getValue().toString();

        counter.setValue(Integer.valueOf(newValue));
        Counter savedCounter = counterRepository.save(counter);

        return convertToResponseDto(savedCounter);
    }

    /**
     * Сбросить счётчик
     */
    @Transactional
    public CounterResponseDto resetCounter(Long characterId, Long counterId) {
        Counter counter = validateAndGetCounter(characterId, counterId);

        String resetValue = strategyFactory
                .getStrategy(counter.getType())
                .getResetValue();

        counter.setValue(Integer.valueOf(resetValue));
        Counter savedCounter = counterRepository.save(counter);

        return convertToResponseDto(savedCounter);
    }

    /**
     * Удалить счётчик
     */
    @Transactional
    public void deleteCounter(Long characterId, Long counterId) {
        Counter counter = validateAndGetCounter(characterId, counterId);
        counterRepository.delete(counter);
    }

    /**
     * Получить счётчик по ID с валидацией принадлежности персонажу
     */
    @Transactional(readOnly = true)
    public CounterResponseDto getCounterById(Long characterId, Long counterId) {
        Counter counter = validateAndGetCounter(characterId, counterId);
        return convertToResponseDto(counter);
    }

    /**
     * Валидация и получение счётчика с проверкой принадлежности персонажу
     */
    private Counter validateAndGetCounter(Long characterId, Long counterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("Character ID не может быть null");
        }
        if (counterId == null) {
            throw new IllegalArgumentException("Counter ID не может быть null");
        }

        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new IllegalArgumentException("Счётчик с ID " + counterId + " не найден"));

        // Проверяем, что счётчик принадлежит указанному персонажу
        if (!counter.getCharacter().getId().equals(characterId)) {
            throw new IllegalArgumentException("Счётчик " + counterId + " не принадлежит персонажу " + characterId);
        }

        return counter;
    }

    private CounterResponseDto convertToResponseDto(Counter counter) {
        CounterResponseDto dto = new CounterResponseDto();
        dto.setId(counter.getId());
        dto.setName(counter.getName());
        dto.setCounterType(counter.getType()); // Используем реальный тип из entity
        dto.setValue(counter.getValue());

        if (counter.getCharacter() != null) {
            dto.setCharacterName(counter.getCharacter().getName());
            dto.setCharacterId(counter.getCharacter().getId());
        }

        return dto;
    }
}
