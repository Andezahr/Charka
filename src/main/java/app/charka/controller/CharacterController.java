package app.charka.controller;

import app.charka.attack.AttackForm;
import app.charka.attack.AttackRules;
import app.charka.attack.AttackService;
import app.charka.attack.AttackSource;
import app.charka.skill.StatValue;
import app.charka.dice.DiceService;
import app.charka.item.CatalogItem;
import app.charka.item.InstalledBlock;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSection;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Character;
import app.charka.model.Inventory;
import app.charka.model.Item;
import app.charka.model.Money;
import app.charka.service.CharacterService;
import app.charka.service.MoneyService;
import app.charka.service.RecurringExpenseService;
import app.charka.system.ExpenseTemplate;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/character/{id}")
public class CharacterController {

    private final CharacterService characterService;
    private final MoneyService moneyService;
    private final RecurringExpenseService recurringExpenseService;
    private final GameSystemRegistry systemRegistry;
    private final DiceService diceService;
    private final AttackService attackService;

    /**
     * A-03: окно «Добавить» по требованию — при первом открытии; app.js хранит его, пока не изменится {@code v}
     * (баланс и подсказки справочника).
     */
    @GetMapping("/add-dialog")
    public String addDialog(@PathVariable Long id, Model model) {
        Character character = characterService.getById(id);
        GameSystem system = systemRegistry.forCharacter(character);
        model.addAttribute("moneySum", moneyService.calculateBalance(id));
        model.addAttribute("currency", system.getCurrency());
        addCatalog(model, system, character);
        return "fragments/items :: add-dialog-body";
    }

    /** A-03: форма правки предмета по требованию — по ✎: поля всех типов предмета весят по 30 КБ на строку */
    @GetMapping("/items/{itemId}/edit-form")
    public String itemEditForm(@PathVariable Long id, @PathVariable Long itemId, Model model) {
        Character character = characterService.getById(id);
        Item item = character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(app.charka.exception.NotFoundException.forId("Item", itemId));
        model.addAttribute("character", character);
        model.addAttribute("item", item);
        model.addAttribute("inv", item.getInventory());
        model.addAttribute("itemTypes", systemRegistry.forCharacter(character).getItemTypes());
        return "fragments/items :: edit-form";
    }

    private static void addCatalog(Model model, GameSystem system, Character character) {
        model.addAttribute("itemTypes", system.getItemTypes());
        model.addAttribute("itemCatalog", system.getItemCatalog().stream()
                .collect(Collectors.groupingBy(CatalogItem::typeCode, Collectors.groupingBy(CatalogItem::group, LinkedHashMap::new, Collectors.toList()))));
        model.addAttribute("suggestedCatalog", system.getSuggestedCatalog(character).stream()
                .collect(Collectors.groupingBy(CatalogItem::typeCode)));
    }

    @GetMapping
    public String characterPage(@PathVariable Long id, Model model) {
        Character character = characterService.getById(id);

        List<Money> moneyList = moneyService.getByCharacter(id);

        long moneySum = moneyService.calculateBalance(id);

        model.addAttribute("character", character);
        model.addAttribute("moneyList", moneyList);
        model.addAttribute("moneySum", moneySum);
        model.addAttribute("expenses", recurringExpenseService.getByCharacter(id));
        GameSystem system = systemRegistry.forCharacter(character);
        model.addAttribute("expenseTemplates", system.getExpenseTemplates().stream()
                .collect(Collectors.groupingBy(ExpenseTemplate::group, LinkedHashMap::new, Collectors.toList())));
        model.addAttribute("currency", system.getCurrency());
        // A-03: окно «Добавить» грузится по требованию; версия меняется вместе с балансом и подсказками справочника
        model.addAttribute("addDialogVersion", Long.toHexString(java.util.Objects.hash(moneySum,
                system.getSuggestedCatalog(character).stream().map(CatalogItem::key).toList())));
        model.addAttribute("effectTemplates", system.getEffectTemplates());
        model.addAttribute("effectStats", system.getCurrentStats(character).values());
        model.addAttribute("luckLeft", java.util.Map.of(character.getId(), system.getLuck(character)));
        model.addAttribute("healthStatus", system.getHealthStatus(character));
        // журнал здоровья: новые записи сверху
        model.addAttribute("woundLog", character.getWounds().stream()
                .sorted(java.util.Comparator.comparing(app.charka.model.Wound::getId, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList());
        model.addAttribute("itemTypes", system.getItemTypes());
        model.addAttribute("itemTypeByCode", system.getItemTypes().stream()
                .collect(Collectors.toMap(ItemTypeDefinition::code, Function.identity())));
        model.addAttribute("itemActions", character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .collect(Collectors.toMap(Item::getId, system::getItemActions)));
        model.addAttribute("itemNotes", character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .collect(Collectors.toMap(Item::getId, system::getItemNotes)));
        model.addAttribute("itemSummaries", character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .collect(Collectors.toMap(Item::getId, system::getItemSummary)));
        InstalledBlock installed = system.getInstalled(character);
        model.addAttribute("installed", installed);
        Set<Long> installedIds = installed == null ? Set.of()
                : installed.rows().stream().map(row -> row.item().getId()).collect(Collectors.toSet());
        // вживлённое не лежит в рюкзаке: в числе предметов инвентаря его нет
        model.addAttribute("installedCounts", installed == null ? Map.of()
                : installed.rows().stream().collect(Collectors.groupingBy(row -> row.item().getInventory().getId(), Collectors.counting())));
        Map<Long, ItemAttachments> attachments = character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .collect(Collectors.toMap(Item::getId, system::getItemAttachments));
        model.addAttribute("itemAttachments", attachments);
        model.addAttribute("inventorySections", character.getInventories().stream()
                .collect(Collectors.toMap(Inventory::getId, inventory -> ItemSection.of(inventory, system.getItemTypes(), attachments, installedIds))));
        AttackRules attackRules = system.getAttackRules();
        if (attackRules != null) {
            Map<Long, AttackForm> attackForms = new HashMap<>();
            Map<String, StatValue> currentStats = system.getCurrentStats(character);
            character.getInventories().stream()
                    .flatMap(inventory -> inventory.getContent().stream())
                    .forEach(item -> {
                        AttackForm form = attackRules.form(AttackSource.of(item), currentStats);
                        if (form != null) {
                            attackForms.put(item.getId(), form);
                        }
                    });
            model.addAttribute("attackForms", attackForms);
            model.addAttribute("attackTargets", character.getCampaign() == null ? List.of()
                    : character.getCampaign().getCharacters().stream()
                    .filter(other -> !other.getId().equals(id))
                    // сначала другая сторона: это самая вероятная цель, и она будет выбрана сразу
                    .sorted(Comparator.comparing(other -> other.isNpc() == character.isNpc()))
                    .toList());
            model.addAttribute("pendingBlasts", attackService.pendingBlasts(id));
            model.addAttribute("attackObjects", attackService.objectTargets(character.getCampaign()));
            // T-08: удар без предмета (CP2020: рукопашная, приёмы) и на листе, не только в трекере
            model.addAttribute("abilities", attackRules.abilities(character).stream()
                    .map(source -> new app.charka.encounter.EncounterView.Ability(source.ability() + "-" + character.getId(),
                            "/character/" + character.getId() + "/attack/ability/" + source.ability(), source.name(),
                            attackRules.form(source, currentStats)))
                    .toList());
        }
        model.addAttribute("roles", system.rolesOf(character));
        model.addAttribute("quickDice", system.getQuickDice());
        model.addAttribute("difficultyPresets", system.getCheckResolver().getDifficultyPresets());
        model.addAttribute("recentRolls", diceService.recentForCharacter(id, 5));

        return "character";
    }
}
