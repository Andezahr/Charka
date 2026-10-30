package app.charka.controller;

import app.charka.dice.DiceService;
import app.charka.item.CatalogItem;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.Money;
import app.charka.service.CharacterService;
import app.charka.service.MoneyService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/character/{id}")
public class CharacterController {

    private final CharacterService characterService;
    private final MoneyService moneyService;
    private final GameSystemRegistry systemRegistry;
    private final DiceService diceService;

    @GetMapping
    public String characterPage(@PathVariable Long id, Model model) {
        Character character = characterService.getById(id);

        List<Money> moneyList = moneyService.getByCharacter(id);

        long moneySum = moneyService.calculateBalance(id);

        model.addAttribute("character", character);
        model.addAttribute("moneyList", moneyList);
        model.addAttribute("moneySum", moneySum);
        GameSystem system = systemRegistry.forCharacter(character);
        model.addAttribute("currency", system.getCurrency());
        model.addAttribute("itemTypes", system.getItemTypes());
        model.addAttribute("itemTypeByCode", system.getItemTypes().stream()
                .collect(Collectors.toMap(ItemTypeDefinition::code, Function.identity())));
        model.addAttribute("itemCatalog", system.getItemCatalog().stream()
                .collect(Collectors.groupingBy(CatalogItem::group, LinkedHashMap::new, Collectors.toList())));
        model.addAttribute("itemActions", character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .collect(Collectors.toMap(Item::getId, system::getItemActions)));
        model.addAttribute("itemNotes", character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .collect(Collectors.toMap(Item::getId, system::getItemNotes)));
        model.addAttribute("roles", system.rolesOf(character));
        model.addAttribute("quickDice", system.getQuickDice());
        model.addAttribute("difficultyPresets", system.getCheckResolver().getDifficultyPresets());
        model.addAttribute("recentRolls", diceService.recentForCharacter(id, 5));

        return "character";
    }
}
