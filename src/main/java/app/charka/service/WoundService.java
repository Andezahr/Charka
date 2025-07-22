package app.charka.service;

import app.charka.model.Wound;
import app.charka.model.Character;
import app.charka.repository.WoundRepository;
import app.charka.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WoundService {

    private static final String WOUND_NOT_FOUND    = "Wound not found for id=%d";
    private static final String CHARACTER_NOT_FOUND = "Character not found for id=%d";

    private final WoundRepository woundRepository;
    private final CharacterRepository characterRepository;

    /**
     * Получить все раны, связанных с указанным персонажем.
     *
     * @param characterId идентификатор персонажа
     * @return список раненых записей; пустой список, если персонаж не найден или ран нет
     */
    @Transactional(readOnly = true)
    public List<Wound> getByCharacter(Long characterId) {
        return woundRepository.findByCharacterId(characterId);
    }

    @Transactional(readOnly = true)
    public Optional<Wound> getById(Long id) {
        return woundRepository.findById(id);
    }

    /**
     * Создать новую запись о ране для указанного персонажа.
     *
     * @param characterId идентификатор персонажа
     * @param wound       объект Wound с заполненными полями name и date
     * @return сохранённая сущность Wound с присвоенным id и привязкой к персонажу
     * @throws IllegalArgumentException если персонаж с данным id не найден
     */
    @Transactional
    public Wound create(Long characterId, Wound wound) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(CHARACTER_NOT_FOUND, characterId)));
        wound.setCharacter(character);
        return woundRepository.save(wound);
    }

    /**
     * Обновить поля существующей раны.
     *
     * @param id            идентификатор раны для обновления
     * @param updatedWound  объект Wound с новыми значениями name и/или date
     * @return обновлённая и сохранённая сущность Wound
     * @throws IllegalArgumentException если рана с данным id не найдена
     */
    @Transactional
    public Wound update(Long id, Wound updatedWound) {
        Wound existing = woundRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(WOUND_NOT_FOUND, id)));
        existing.setName(updatedWound.getName());
        existing.setDate(updatedWound.getDate());
        // персонаж не меняется в рамках обновления раны
        return woundRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Wound existing = woundRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(WOUND_NOT_FOUND, id)));
        woundRepository.delete(existing);
    }
}
