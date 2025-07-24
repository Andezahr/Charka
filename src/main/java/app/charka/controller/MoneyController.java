package app.charka.controller;

import app.charka.model.Money;
import app.charka.service.MoneyService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/character/{characterId}/money")
public class MoneyController {

    private final MoneyService moneyService;

    public MoneyController(MoneyService moneyService) {
        this.moneyService = moneyService;
    }

    @PostMapping
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
        return "redirect:/character/" + characterId;
    }

    @PostMapping("/{moneyId}/delete")
    public String deleteMoney(
            @PathVariable Long characterId,
            @PathVariable Long moneyId
    ) {
        moneyService.delete(moneyId);
        return "redirect:/character/" + characterId;
    }
}
