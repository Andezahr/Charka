package app.charka.service;

import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.model.RecurringExpense;
import app.charka.repository.CharacterRepository;
import app.charka.repository.RecurringExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Периодические траты: заводятся на листе, списываются в деньги, когда кампания проматывает время.
 */
@Service
@RequiredArgsConstructor
public class RecurringExpenseService {

    /** Сколько списаний за одну промотку на одну трату: защита от опечатки «период 1 день» на десять лет */
    private static final int MAX_CHARGES = 3660;

    private final RecurringExpenseRepository expenseRepository;
    private final CharacterRepository characterRepository;
    private final MoneyService moneyService;

    @Transactional(readOnly = true)
    public List<RecurringExpense> getByCharacter(Long characterId) {
        return expenseRepository.findByCharacterIdOrderByNextDate(characterId);
    }

    /**
     * Новая трата. Первое списание — через период от текущей игровой даты: заведённое сегодня уже оплачено.
     *
     * @throws IllegalArgumentException если сумма или период не больше нуля
     */
    @Transactional
    public RecurringExpense create(Long characterId, String name, long amount, int periodDays) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Назовите трату");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Сумма траты больше нуля");
        }
        if (periodDays < 1) {
            throw new IllegalArgumentException("Период — от 1 дня");
        }
        Character character = characterRepository.findById(characterId)
                .orElseThrow(NotFoundException.forId("Character", characterId));
        RecurringExpense expense = new RecurringExpense();
        expense.setName(name.strip());
        expense.setAmount(amount);
        expense.setPeriodDays(periodDays);
        expense.setNextDate(gameDate(character).plusDays(periodDays));
        expense.setCharacter(character);
        character.getRecurringExpenses().add(expense);
        return expenseRepository.save(expense);
    }

    /**
     * @throws NotFoundException если траты нет или она чужая
     */
    @Transactional
    public void delete(Long characterId, Long expenseId) {
        RecurringExpense expense = expenseRepository.findById(expenseId)
                .filter(found -> found.getCharacter().getId().equals(characterId))
                .orElseThrow(NotFoundException.forId("RecurringExpense", expenseId));
        expense.getCharacter().getRecurringExpenses().remove(expense);
        expenseRepository.delete(expense);
    }

    /**
     * Списать всё, что наступило к {@code upTo}. За одну промотку трата пишется одной операцией датой последнего списания:
     * месяц ночёвок в капсуле — «Coffin ×30», а не тридцать строк.
     *
     * @return сколько списаний сделано
     */
    @Transactional
    public int chargeDue(Campaign campaign, LocalDate upTo) {
        int charged = 0;
        for (Character character : campaign.getCharacters()) {
            for (RecurringExpense expense : expenseRepository.findByCharacterIdOrderByNextDate(character.getId())) {
                int times = 0;
                LocalDate last = null;
                while (!expense.getNextDate().isAfter(upTo) && times < MAX_CHARGES) {
                    last = expense.getNextDate();
                    expense.setNextDate(expense.getNextDate().plusDays(expense.getPeriodDays()));
                    times++;
                }
                if (times > 0) {
                    Money money = new Money();
                    money.setName(times > 1 ? expense.getName() + " ×" + times : expense.getName());
                    money.setAmount(-expense.getAmount() * times);
                    money.setOperationDate(last);
                    moneyService.create(character.getId(), money);
                }
                charged += times;
            }
        }
        return charged;
    }

    private static LocalDate gameDate(Character character) {
        Campaign campaign = character.getCampaign();
        return campaign == null || campaign.getCurrentDate() == null ? LocalDate.now() : campaign.getCurrentDate();
    }
}
