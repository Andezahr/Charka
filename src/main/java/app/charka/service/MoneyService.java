package app.charka.service;

import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.repository.CharacterRepository;
import app.charka.repository.MoneyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MoneyService {

    private static final String CHARACTER_NOT_FOUND = "Character not found for id=%d";

    private final MoneyRepository moneyRepository;
    private final CharacterRepository characterRepository;

    /**
     * Получить все операции (Money) для заданного персонажа.
     *
     * @param characterId идентификатор персонажа
     * @return список операций; пустой список, если персонаж не найден или операций нет
     */
    @Transactional(readOnly = true)
    public List<Money> getByCharacter(Long characterId) {
        return moneyRepository.findByCharacterId(characterId);
    }

    @Transactional(readOnly = true)
    public Money findById(Long id) {
        return moneyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Operation not found for ID: " + id));
    }

    /**
     * Создать новую финансовую операцию и привязать к персонажу.
     * Если дата операции не задана, ставится текущая игровая дата кампании персонажа
     * (персонаж без кампании — реальная дата).
     *
     * @param characterId идентификатор персонажа
     * @param money       объект Money с заполненными полями amount, name и, необязательно, operationDate
     * @return сохранённая сущность Money с присвоенным id
     * @throws NotFoundException если персонаж не найден
     */
    @Transactional
    public Money create(Long characterId, Money money) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new NotFoundException(
                        String.format(CHARACTER_NOT_FOUND, characterId)));
        money.setCharacter(character);
        if (money.getOperationDate() == null) {
            money.setOperationDate(gameDate(character));
        }
        return moneyRepository.save(money);
    }

    /**
     * Текущая игровая дата персонажа: дата его кампании, без кампании — реальная.
     */
    static LocalDate gameDate(Character character) {
        Campaign campaign = character.getCampaign();
        if (campaign == null || campaign.getCurrentDate() == null) {
            return LocalDate.now();
        }
        return campaign.getCurrentDate();
    }

    /**
     * Обновить существующую финансовую операцию.
     *
     * @param id           идентификатор операции
     * @param updatedMoney объект Money с новыми значениями amount, name, operationDate
     * @return обновлённая и сохранённая сущность Money
     * @throws NotFoundException если операция не найдена
     */
    @Transactional
    public Money update(Long id, Money updatedMoney) {
        Money existing = moneyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Money entry not found for id=" + id));
        existing.setAmount(updatedMoney.getAmount());
        existing.setName(updatedMoney.getName());
        existing.setOperationDate(updatedMoney.getOperationDate());
        return moneyRepository.save(existing);
    }

    /**
     * Удалить финансовую операцию по её идентификатору.
     *
     * @param id идентификатор операции для удаления
     * @throws NotFoundException если операция не найдена
     */
    @Transactional
    public void delete(Long id) {
        Money existing = moneyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Money entry not found for id=" + id));
        moneyRepository.delete(existing);
    }

    /**
     * Вычислить сумму всех операций персонажа.
     *
     * @param characterId идентификатор персонажа
     * @return итоговая сумма (доходы минус расходы)
     * @throws NotFoundException если персонаж не найден
     */
    @Transactional(readOnly = true)
    public long calculateBalance(Long characterId) {
        return moneyRepository.findByCharacterId(characterId)
                .stream()
                .mapToLong(Money::getAmount)
                .sum();
    }

    /**
     * Получить список операций с положительным amount (доходы) для персонажа.
     *
     * @param characterId идентификатор персонажа
     * @return список операций с amount > 0; пустой список, если нет доходов
     * @throws NotFoundException если персонаж не найден
     */
    @Transactional(readOnly = true)
    public List<Money> getIncomes(Long characterId) {
        return moneyRepository.findByCharacterId(characterId)
                .stream()
                .filter(m -> m.getAmount() != null && m.getAmount() > 0)
                .toList();
    }

    /**
     * Получить список операций с отрицательным amount (расходы) для персонажа.
     *
     * @param characterId идентификатор персонажа
     * @return список операций с amount < 0; пустой список, если нет расходов
     * @throws NotFoundException если персонаж не найден
     */
    @Transactional(readOnly = true)
    public List<Money> getExpenses(Long characterId) {
        return moneyRepository.findByCharacterId(characterId)
                .stream()
                .filter(m -> m.getAmount() != null && m.getAmount() < 0)
                .toList();
    }

}
