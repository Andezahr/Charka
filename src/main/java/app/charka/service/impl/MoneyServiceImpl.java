package app.charka.service.impl;

import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.repository.CharacterRepository;
import app.charka.repository.MoneyRepository;
import app.charka.service.MoneyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MoneyServiceImpl implements MoneyService {

    private static final String CHARACTER_NOT_FOUND = "Character not found for id=%d";

    private final MoneyRepository moneyRepository;
    private final CharacterRepository characterRepository;

    /**
     * Получить все операции (Money) для заданного персонажа.
     *
     * @param characterId идентификатор персонажа
     * @return список операций; пустой список, если персонаж не найден или операций нет
     */
    @Transactional(readOnly = true) @Override public List<Money> getByCharacter(Long characterId) {
        return moneyRepository.findByCharacterId(characterId);
    }

    @Transactional(readOnly = true) @Override public Money findById(Long id) {
        return moneyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found for ID: " + id));
    }

    /**
     * Создать новую финансовую операцию и привязать к персонажу.
     *
     * @param characterId идентификатор персонажа
     * @param money       объект Money с заполненными полями amount, name, operationDate
     * @return сохранённая сущность Money с присвоенным id
     * @throws IllegalArgumentException если персонаж не найден
     */
    @Transactional @Override public Money create(Long characterId, Money money) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(CHARACTER_NOT_FOUND, characterId)));
        money.setCharacter(character);
        return moneyRepository.save(money);
    }

    /**
     * Обновить существующую финансовую операцию.
     *
     * @param id           идентификатор операции
     * @param updatedMoney объект Money с новыми значениями amount, name, operationDate
     * @return обновлённая и сохранённая сущность Money
     * @throws IllegalArgumentException если операция не найдена
     */
    @Transactional @Override public Money update(Long id, Money updatedMoney) {
        Money existing = moneyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
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
     * @throws IllegalArgumentException если операция не найдена
     */
    @Transactional @Override public void delete(Long id) {
        Money existing = moneyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Money entry not found for id=" + id));
        moneyRepository.delete(existing);
    }

    /**
     * Вычислить сумму всех операций персонажа.
     *
     * @param characterId идентификатор персонажа
     * @return итоговая сумма (доходы минус расходы)
     * @throws IllegalArgumentException если персонаж не найден
     */
    @Transactional(readOnly = true) @Override public long calculateBalance(Long characterId) {
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
     * @throws IllegalArgumentException если персонаж не найден
     */
    @Transactional(readOnly = true) @Override public List<Money> getIncomes(Long characterId) {
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
     * @throws IllegalArgumentException если персонаж не найден
     */
    @Transactional(readOnly = true) @Override public List<Money> getExpenses(Long characterId) {
        return moneyRepository.findByCharacterId(characterId)
                .stream()
                .filter(m -> m.getAmount() != null && m.getAmount() < 0)
                .toList();
    }

}
