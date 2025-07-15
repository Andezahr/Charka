package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.repository.CharacterRepository;
import app.charka.repository.MoneyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class CharacterController {

    @Autowired
    private CharacterRepository characterRepository;
    @Autowired
    private MoneyRepository moneyRepository;

    @GetMapping(Routes.CHARACTER)
    public String characterPage(@PathVariable Long id, Model model) {
        Character character = characterRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid character Id:" + id));

        // Получаем список операций с деньгами для персонажа
        List<Money> moneyList = moneyRepository.findByCharacterId(id);

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
