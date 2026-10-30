package app.charka.service;

import app.charka.model.Money;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MoneyService {

    @Transactional(readOnly = true)
    List<Money> getByCharacter(Long characterId);

    @Transactional(readOnly = true)
    Money findById(Long id);

    @Transactional
    Money create(Long characterId, Money money);

    @Transactional
    Money update(Long id, Money updatedMoney);

    @Transactional
    void delete(Long id);

    @Transactional(readOnly = true)
    long calculateBalance(Long characterId);

    @Transactional(readOnly = true)
    List<Money> getIncomes(Long characterId);

    @Transactional(readOnly = true)
    List<Money> getExpenses(Long characterId);
}
