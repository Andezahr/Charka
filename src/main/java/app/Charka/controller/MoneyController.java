package app.Charka.controller;

import app.Charka.Routes;
import app.Charka.model.Character;
import app.Charka.model.Money;
import app.Charka.repository.CharacterRepository;
import app.Charka.repository.MoneyRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class MoneyController {

    private final MoneyRepository moneyRepository;
    private final CharacterRepository characterRepository;

    public MoneyController(MoneyRepository moneyRepository, CharacterRepository characterRepository) {
        this.moneyRepository = moneyRepository;
        this.characterRepository = characterRepository;
    }

    @PostMapping(Routes.MONEY_ADD)
    public String addMoney(
            @PathVariable Long characterId,
            @RequestParam String name,
            @RequestParam Long amount
    ) {
        Character character = characterRepository.findById(characterId).orElseThrow();
        Money money = new Money();
        money.setCharacter(character);
        money.setName(name);
        money.setAmount(amount);
        money.setOperationDate(LocalDate.now());
        moneyRepository.save(money);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.MONEY_DELETE)
    public String deleteMoney(
            @PathVariable Long characterId,
            @PathVariable Long moneyId
    ) {
        moneyRepository.deleteById(moneyId);
        return Routes.CHARACTER_REDIRECT + characterId;
    }
}
