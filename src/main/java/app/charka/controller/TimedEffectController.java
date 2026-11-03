package app.charka.controller;

import app.charka.effect.EffectTemplate;
import app.charka.effect.TimedEffectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Блок «Эффекты» на листе: препараты и свои временные эффекты.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/character/{characterId}/effects")
public class TimedEffectController {

    private static final String BACK = "#effects";

    private final TimedEffectService effectService;

    @PostMapping
    public String create(@PathVariable Long characterId, @RequestParam(required = false) String template,
                         @RequestParam(required = false) String name, @RequestParam(required = false) String stat,
                         @RequestParam(defaultValue = "0") int modifier, @RequestParam(defaultValue = "1") String duration,
                         @RequestParam(defaultValue = "ROUND") EffectTemplate.DurationUnit unit,
                         @RequestParam(required = false) String note, RedirectAttributes redirectAttributes) {
        try {
            String result = template != null && !template.isBlank()
                    ? effectService.applyTemplate(characterId, template)
                    : effectService.create(characterId, name, stat, modifier, duration, unit, note);
            redirectAttributes.addFlashAttribute("effectResult", result);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/character/" + characterId + BACK;
    }

    @PostMapping("/{effectId}/delete")
    public String delete(@PathVariable Long characterId, @PathVariable Long effectId) {
        effectService.remove(characterId, effectId);
        return "redirect:/character/" + characterId + BACK;
    }
}
