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
    private final CounterModel counterModel;

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

    /** R-07: «Сделать основным» в меню ⋯ оружия; пусто — снова первое оружие инвентаря */
    @org.springframework.web.bind.annotation.PostMapping("/main-weapon")
    public String mainWeapon(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestParam(required = false) Long item) {
        characterService.setMainWeapon(id, item);
        return "redirect:/character/" + id + "#weapons";
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
        fill(id, model, false);
        return "character";
    }

    /** R-08: вкладка «Снаряжение» — все инвентари (и неносимые), вживлённое целиком, деньги и регулярные траты */
    @GetMapping("/gear")
    public String gearPage(@PathVariable Long id, Model model) {
        fill(id, model, true);
        return "gear";
    }

    /**
     * Модель листа и «Снаряжения»: строки предметов одни и те же.
     *
     * @param gear «Снаряжение»: все инвентари и оружие в них; иначе лист — носимые инвентари, оружие отдельной карточкой
     */
    private void fill(Long id, Model model, boolean gear) {
        Character character = characterService.getById(id);
        List<Inventory> shownInventories = character.getInventories().stream()
                .filter(inventory -> gear || inventory.isCarried())
                .sorted(Comparator.comparing(Inventory::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        model.addAttribute("shownInventories", shownInventories);

        List<Money> moneyList = moneyService.getByCharacter(id);

        long moneySum = moneyService.calculateBalance(id);

        model.addAttribute("character", character);
        // R-05: шапка состояния (компактные счётчики системы) и карточка «Счётчики»
        counterModel.fill(id, model);
        model.addAttribute("moneyList", moneyList);
        // R-08: на листе — три последние записи, полный журнал — в «Снаряжении»
        model.addAttribute("recentMoney", moneyList.stream()
                .sorted(Comparator.comparing(Money::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .toList());
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
        // R-07: что показано не в инвентаре — вживлённое и оружие
        Set<Long> shownElsewhere = new java.util.HashSet<>(installedIds);
        AttackRules attackRules = system.getAttackRules();
        if (attackRules != null) {
            Map<Long, AttackForm> attackForms = new HashMap<>();
            Map<String, String> attackBases = new HashMap<>();
            Map<String, StatValue> currentStats = system.getCurrentStats(character);
            character.getInventories().stream()
                    .flatMap(inventory -> inventory.getContent().stream())
                    .forEach(item -> {
                        AttackForm form = attackRules.form(AttackSource.of(item), currentStats);
                        if (form != null) {
                            attackForms.put(item.getId(), form);
                            String base = attackRules.attackBase(character, AttackSource.of(item), form, currentStats);
                            if (base != null) {
                                attackBases.put("item-" + item.getId(), base);
                            }
                        }
                    });
            model.addAttribute("attackForms", attackForms);
            // R-07: оружие — отдельной карточкой: основное сверху, запасное свёрнуто; вживлённое остаётся в своём блоке
            // R-08: на листе — только из носимых инвентарей; в «Снаряжении» оружие лежит в своих инвентарях
            List<Item> weapons = gear ? List.of() : shownInventories.stream()
                    .flatMap(inventory -> inventory.getContent().stream())
                    .filter(item -> attackForms.containsKey(item.getId()) && !installedIds.contains(item.getId()))
                    .toList();
            Item mainWeapon = weapons.stream()
                    .filter(item -> item.getId().equals(character.getMainWeaponId()))
                    .findFirst()
                    .orElse(weapons.isEmpty() ? null : weapons.get(0));
            model.addAttribute("mainWeapon", mainWeapon);
            model.addAttribute("spareWeapons", weapons.stream().filter(item -> item != mainWeapon).toList());
            weapons.forEach(item -> shownElsewhere.add(item.getId()));
            model.addAttribute("inventoryWeapons", weapons.stream()
                    .collect(Collectors.groupingBy(item -> item.getInventory().getId())));
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
            attackRules.abilities(character).forEach(source -> {
                String base = attackRules.attackBase(character, source, attackRules.form(source, currentStats), currentStats);
                if (base != null) {
                    attackBases.put(source.ability() + "-" + character.getId(), base);
                }
            });
            model.addAttribute("attackBases", attackBases);
        }
        if (gear) {
            model.addAttribute("inventoryWeapons", Map.of());
        }
        Set<Long> excluded = Set.copyOf(shownElsewhere);
        model.addAttribute("inventorySections", character.getInventories().stream()
                .collect(Collectors.toMap(Inventory::getId, inventory -> ItemSection.of(inventory, system.getItemTypes(), attachments, excluded))));
        model.addAttribute("roles", system.rolesOf(character));
        model.addAttribute("quickDice", system.getQuickDice());
        model.addAttribute("difficultyPresets", system.getCheckResolver().getDifficultyPresets());
        model.addAttribute("recentRolls", diceService.recentForCharacter(id, 5));

    }
}
