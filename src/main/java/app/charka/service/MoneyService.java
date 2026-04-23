package app.charka.service;

import app.charka.model.Money;

import java.util.List;

public interface MoneyService {
    List<Money> getByCharacter(Long characterId);
    Money findById(Long id);
    Money create(Long characterId, Money money);
    Money update(Long id, Money updatedMoney);
    void delete(Long id);
    long calculateBalance(Long characterId);
    List<Money> getIncomes(Long characterId);
    List<Money> getExpenses(Long characterId);
}
