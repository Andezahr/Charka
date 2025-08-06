package app.charka.service.counter;

import app.charka.dto.counter.request.CounterCreateRequestDto;
import app.charka.dto.counter.request.CounterUpdateRequestDto;
import app.charka.dto.counter.response.CounterResponseDto;
import app.charka.exception.CharacterNotFoundException;
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

    @Transactional
    public CounterResponseDto createCounter(CounterCreateRequestDto requestDto) {

        Character character = characterRepository.findById(requestDto.getCharacterId())
                .orElseThrow(CharacterNotFoundException.forId(requestDto.getCharacterId()));

        Counter counter = new Counter();
        counter.setName(requestDto.getName());
        counter.setValue(requestDto.getInitialValue());
        counter.setType(requestDto.getCounterType());
        counter.setCharacter(character);

        Counter savedCounter = counterRepository.save(counter);
        return convertToResponseDto(savedCounter);
    }

    @Transactional(readOnly = true)
    public List<CounterResponseDto> getAllCountersByCharacterId(Long characterId) {

        List<Counter> counters = counterRepository.findByCharacterId(characterId);
        return counters.stream()
                .map(this::convertToResponseDto)
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

        String resetValue = strategyFactory
                .getStrategy(counter.getType())
                .getResetValue();

        counter.setValue(Integer.valueOf(resetValue));
        Counter savedCounter = counterRepository.save(counter);

        return convertToResponseDto(savedCounter);
    }

    @Transactional
    public void deleteCounter(Long characterId, Long counterId) {
        Counter counter = validateAndGetCounter(characterId, counterId);
        counterRepository.delete(counter);
    }

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

        if (!counter.getCharacter().getId().equals(characterId)) {
            throw new IllegalArgumentException("Счётчик " + counterId + " не принадлежит персонажу " + characterId);
        }

        return counter;
    }

    private CounterResponseDto convertToResponseDto(Counter counter) {
        CounterResponseDto dto = new CounterResponseDto();
        dto.setId(counter.getId());
        dto.setName(counter.getName());
        dto.setCounterType(counter.getType());
        dto.setValue(counter.getValue());

        if (counter.getCharacter() != null) {
            dto.setCharacterName(counter.getCharacter().getName());
            dto.setCharacterId(counter.getCharacter().getId());
        }

        return dto;
    }
}
