package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.service.CharacterService;
import app.charka.service.MoneyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;
    private final MoneyService moneyService;

    @GetMapping(Routes.CHARACTER)
    public String characterPage(@PathVariable Long id, Model model) {
        Character character = characterService
                .getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid character Id:" + id));

        // Получаем список операций с деньгами для персонажа
        List<Money> moneyList = moneyService.getByCharacter(id);

        // Считаем сумму всех операций
        long moneySum = moneyList.stream()
                .mapToLong(Money::getAmount)
                .sum();

        model.addAttribute("character", character);
        model.addAttribute("moneyList", moneyList);
        model.addAttribute("moneySum", moneySum);

        return "character";
    }
}
