package app.charka.service.impl;

import app.charka.exception.NotFoundException;
import app.charka.model.Character;
import app.charka.model.Wound;
import app.charka.repository.CharacterRepository;
import app.charka.repository.WoundRepository;
import app.charka.service.WoundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WoundServiceImpl implements WoundService {

    private static final String WOUND_NOT_FOUND = "Wound not found for id=%d";
    private static final String CHARACTER_NOT_FOUND = "Character not found for id=%d";

    private final WoundRepository woundRepository;
    private final CharacterRepository characterRepository;

    /**
     * Получить все раны, связанные с указанным персонажем.
     *
     * @param characterId идентификатор персонажа
     * @return список ран; пустой список, если персонаж не найден или ран нет
     */
    @Override
    public List<Wound> getByCharacter(Long characterId) {
        return woundRepository.findByCharacterId(characterId);
    }

    /**
     * Получить рану по идентификатору.
     *
     * @param id идентификатор раны
     * @return Optional с раной, если найдена
     */
    @Override
    public Wound getById(Long id) {
        return woundRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                String.format(WOUND_NOT_FOUND, id)));
    }

    /**
     * Создать новую запись о ране для указанного персонажа.
     * Если дата не задана, ставится текущая игровая дата кампании персонажа.
     *
     * @param characterId идентификатор персонажа
     * @param wound       объект Wound с заполненными полями name и date
     * @return сохранённая сущность Wound с присвоенным id и привязкой к персонажу
     * @throws NotFoundException если персонаж с данным id не найден
     */
    @Override
    public Wound create(Long characterId, Wound wound) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new NotFoundException(
                        String.format(CHARACTER_NOT_FOUND, characterId)));
        wound.setCharacter(character);
        if (wound.getDate() == null) {
            wound.setDate(MoneyServiceImpl.gameDate(character));
        }
        return woundRepository.save(wound);
    }

    /**
     * Обновить поля существующей раны.
     *
     * @param id            идентификатор раны для обновления
     * @param updatedWound  объект Wound с новыми значениями name и/или date
     * @return обновлённая и сохранённая сущность Wound
     * @throws NotFoundException если рана с данным id не найдена
     */
    @Override
    public Wound update(Long id, Wound updatedWound) {
        Wound existing = woundRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format(WOUND_NOT_FOUND, id)));
        existing.setName(updatedWound.getName());
        existing.setSeverity(updatedWound.getSeverity());
        existing.setDate(updatedWound.getDate());
        return woundRepository.save(existing);
    }

    /**
     * Удалить рану по идентификатору.
     *
     * @param id идентификатор раны для удаления
     * @throws NotFoundException если рана с данным id не найдена
     */
    @Override
    public void delete(Long id) {
        if (!woundRepository.existsById(id)) {
            throw new NotFoundException(String.format(WOUND_NOT_FOUND, id));
        }
        woundRepository.deleteById(id);
    }
}
