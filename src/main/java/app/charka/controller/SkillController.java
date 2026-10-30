package app.charka.controller;

import app.charka.check.CheckResolver;
import app.charka.model.Character;
import app.charka.service.CharacterService;
import app.charka.service.counter.CounterService;
import app.charka.skill.SkillImprovement;
import app.charka.skill.SkillService;
import app.charka.skill.SkillSheet;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Страница навыков и роль персонажа. Некорректный ввод — flash {@code errorMessage}.
 * Проверка навыка — {@code api.SkillCheckController}.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/character/{characterId}")
public class SkillController {

    private static final String LEVEL_PREFIX = "level.";
    private static final String CUSTOM_PREFIX = "custom.";

    private final SkillService skillService;
    private final CharacterService characterService;
    private final CounterService counterService;
    private final GameSystemRegistry systemRegistry;

    @GetMapping("/skills")
    public String skills(@PathVariable Long characterId, Model model) {
        Character character = characterService.getById(characterId);
        if (!skillService.hasCatalog(characterId)) {
            return redirectToSheet(characterId);
        }
        CheckResolver resolver = systemRegistry.forCharacter(character).getCheckResolver();
        model.addAttribute("character", character);
        model.addAttribute("sheet", skillService.getSheet(characterId));
        model.addAttribute("difficultyPresets", resolver.getDifficultyPresets());
        model.addAttribute("opponents", character.getCampaign() == null
                ? List.of()
                : characterService.getCampaignCharacters(character.getCampaign()).stream()
                        .filter(other -> !other.getId().equals(characterId))
                        .toList());
        return "skills/list";
    }

    /**
     * Уровни приходят полями {@code level.<код навыка>} и {@code custom.<id строки>}.
     */
    @PostMapping("/skills")
    public String updateLevels(@PathVariable Long characterId,
                               @RequestParam Map<String, String> params,
                               RedirectAttributes redirectAttributes) {
        return handle(characterId, redirectAttributes, "Навыки сохранены", () -> {
            Map<String, Integer> levels = new HashMap<>();
            Map<Long, Integer> customLevels = new HashMap<>();
            params.forEach((key, value) -> {
                if (key.startsWith(LEVEL_PREFIX)) {
                    levels.put(key.substring(LEVEL_PREFIX.length()), parseLevel(value));
                } else if (key.startsWith(CUSTOM_PREFIX)) {
                    customLevels.put(Long.valueOf(key.substring(CUSTOM_PREFIX.length())), parseLevel(value));
                }
            });
            skillService.updateLevels(characterId, levels, customLevels);
        });
    }

    @PostMapping("/skills/custom")
    public String addCustom(@PathVariable Long characterId,
                            @RequestParam String code,
                            @RequestParam String customName,
                            @RequestParam(defaultValue = "0") int level,
                            RedirectAttributes redirectAttributes) {
        return handle(characterId, redirectAttributes, "Навык добавлен",
                () -> skillService.addCustom(characterId, code, customName, level));
    }

    @PostMapping("/skills/custom/{skillId}/delete")
    public String deleteCustom(@PathVariable Long characterId, @PathVariable Long skillId,
                               RedirectAttributes redirectAttributes) {
        return handle(characterId, redirectAttributes, "Навык удалён",
                () -> skillService.deleteCustom(characterId, skillId));
    }

    /**
     * @param role отмеченные роли; ничего не отмечено — снять все
     */
    @PostMapping("/roles")
    public String setRoles(@PathVariable Long characterId,
                           @RequestParam(required = false) List<String> role,
                           RedirectAttributes redirectAttributes) {
        return handle(characterId, redirectAttributes, "Роли сохранены",
                () -> skillService.setRoles(characterId, role));
    }

    /**
     * Повышение навыка за очки; после него страница возвращается к строке этого навыка.
     *
     * @param skillId строка навыка со своим названием; иначе навык определяется по {@code code}
     */
    @PostMapping("/skills/improve")
    public String improve(@PathVariable Long characterId,
                          @RequestParam(required = false) String code,
                          @RequestParam(required = false) Long skillId,
                          RedirectAttributes redirectAttributes) {
        handle(characterId, redirectAttributes, null, () -> {
            SkillImprovement improvement = skillService.improve(characterId, code, skillId);
            redirectAttributes.addFlashAttribute("successMessage", improvement.name() + ": уровень "
                    + improvement.level() + ", потрачено очков: " + improvement.cost());
        });
        return "redirect:/character/" + characterId + "/skills#" + SkillSheet.Row.anchor(code, skillId);
    }

    /**
     * Создаёт недостающие счётчики системы: без статов итоги навыков считаются только по уровню.
     */
    @PostMapping("/skills/setup")
    public String setupCounters(@PathVariable Long characterId, RedirectAttributes redirectAttributes) {
        return handle(characterId, redirectAttributes, "Счётчики созданы — задайте значения на вкладке «Счётчики»", () -> {
            Character character = characterService.getById(characterId);
            counterService.createDefaults(characterId, systemRegistry.forCharacter(character).getDefaultCounterTypes());
        });
    }

    private static String handle(Long characterId, RedirectAttributes redirectAttributes, String success, Runnable action) {
        try {
            action.run();
            if (success != null) {
                redirectAttributes.addFlashAttribute("successMessage", success);
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/character/" + characterId + "/skills";
    }

    private static int parseLevel(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Уровень навыка должен быть числом: " + value);
        }
    }

    private static String redirectToSheet(Long characterId) {
        return "redirect:/character/" + characterId;
    }
}
