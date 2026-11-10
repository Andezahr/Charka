package app.charka.controller;

import app.charka.item.InstalledBlock;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.service.CharacterService;
import app.charka.service.MoneyService;
import app.charka.service.campaign.CampaignService;
import app.charka.skill.CharacterRole;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import app.charka.system.PrintSheet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Лист на бумаге и в PDF (A-12): запасной вариант, если сеть упадёт. Отдельная страница со своими стилями печати,
 * PDF — печатью браузера. Мастеру — все персонажи кампании подряд, каждый с новой страницы.
 */
@Controller
@RequiredArgsConstructor
public class PrintController {

    private final CharacterService characterService;
    private final CampaignService campaignService;
    private final MoneyService moneyService;
    private final GameSystemRegistry systemRegistry;

    /**
     * Лист одного персонажа.
     *
     * @param sections снаряжение по типам: «Оружие» → строки «Arasaka Minami 10 · WA 0 · 2D6+3 · ROF 20»
     * @param installed вживлённое (кибер), отдельно от снаряжения
     */
    public record Sheet(Character character, List<String> roles, PrintSheet system, Map<String, List<String>> sections,
                        List<String> installed, long money, String currency) {
    }

    @GetMapping("/character/{id}/print")
    @Transactional(readOnly = true)
    public String character(@PathVariable Long id, Model model) {
        Character character = characterService.getById(id);
        model.addAttribute("title", character.getName());
        model.addAttribute("sheets", List.of(sheet(character)));
        return "print";
    }

    /** @param npc и NPC тоже; по умолчанию только персонажи игроков */
    @GetMapping("/campaign/{campaignId}/print")
    @Transactional(readOnly = true)
    public String campaign(@PathVariable Long campaignId, @RequestParam(defaultValue = "false") boolean npc, Model model) {
        Campaign campaign = campaignService.getById(campaignId);
        model.addAttribute("title", campaign.getName());
        model.addAttribute("sheets", campaign.getCharacters().stream()
                .filter(character -> npc || !character.isNpc())
                .map(this::sheet).toList());
        return "print";
    }

    private Sheet sheet(Character character) {
        GameSystem system = systemRegistry.forCharacter(character);
        InstalledBlock block = system.getInstalled(character);
        Set<Long> installedIds = block == null ? Set.of() : block.rows().stream().map(row -> row.item().getId()).collect(Collectors.toSet());
        Map<String, ItemTypeDefinition> types = system.getItemTypes().stream()
                .collect(Collectors.toMap(ItemTypeDefinition::code, type -> type, (first, second) -> first, LinkedHashMap::new));
        Map<String, List<String>> sections = new LinkedHashMap<>();
        types.values().forEach(type -> sections.put(type.displayName(), new ArrayList<>()));
        character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(item -> !installedIds.contains(item.getId()))
                .forEach(item -> {
                    ItemTypeDefinition type = types.get(item.getType());
                    String section = type == null ? "Прочее" : type.displayName();
                    sections.computeIfAbsent(section, key -> new ArrayList<>()).add(line(item, type));
                });
        sections.values().removeIf(List::isEmpty);
        List<String> installed = block == null ? List.of() : block.rows().stream()
                .map(row -> row.item().getName() + (row.details().isEmpty() ? "" : " · " + String.join(" · ", row.details())))
                .toList();
        return new Sheet(character, system.rolesOf(character).stream().map(CharacterRole::name).toList(),
                system.getPrintSheet(character), sections, installed, moneyService.calculateBalance(character.getId()),
                system.getCurrency().symbol());
    }

    /** «Arasaka Minami 10 · WA 0 · Урон 2D6+3 · ROF 20 · × 2»: все поля типа, чтобы играть без приложения */
    private static String line(Item item, ItemTypeDefinition type) {
        List<String> parts = new ArrayList<>();
        parts.add(item.getName());
        if (type != null) {
            type.describeReadable(item.getProperties()).stream().filter(Objects::nonNull).forEach(parts::add);
        }
        if (item.getQuantity() != null && item.getQuantity() > 1) {
            parts.add("× " + item.getQuantity());
        }
        return String.join(" · ", parts);
    }
}
