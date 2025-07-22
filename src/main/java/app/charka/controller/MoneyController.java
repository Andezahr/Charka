package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Money;
import app.charka.service.MoneyService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class MoneyController {

    private final MoneyService moneyService;

    public MoneyController(MoneyService moneyService) {
        this.moneyService = moneyService;
    }

    @PostMapping(Routes.MONEY_ADD)
    public String addMoney(
            @PathVariable Long characterId,
            @RequestParam String name,
            @RequestParam Long amount
    ) {
        Money money = new Money();
        money.setName(name);
        money.setAmount(amount);
        money.setOperationDate(LocalDate.now());
        moneyService.create(characterId, money);
        return Routes.CHARACTER_REDIRECT + characterId;
    }

    @PostMapping(Routes.MONEY_DELETE)
    public String deleteMoney(
            @PathVariable Long characterId,
            @PathVariable Long moneyId
    ) {
        moneyService.delete(moneyId);
        return Routes.CHARACTER_REDIRECT + characterId;
    }
}
