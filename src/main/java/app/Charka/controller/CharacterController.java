package app.Charka.controller;

import app.Charka.Routes;
import app.Charka.model.Character;
import app.Charka.model.Inventory;
import app.Charka.model.Item;
import app.Charka.model.Money;
import app.Charka.repository.CharacterRepository;
import app.Charka.repository.InventoryRepository;
import app.Charka.repository.ItemRepository;
import app.Charka.repository.MoneyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
