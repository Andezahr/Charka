package app.charka.controller;

import app.charka.dice.DiceResult;
import app.charka.dice.DiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Броски кубиков с листа персонажа и со страницы кампании. Результат — во flash {@code rollResult}/{@code rollLabel},
 * непонятное выражение — во flash {@code rollError}.
 */
@Controller
@RequiredArgsConstructor
public class DiceController {

    private final DiceService diceService;

    /**
     * @param quick выражение с быстрой кнопки; если задано, поле {@code expression} не используется
     */
    @PostMapping("/character/{characterId}/roll")
    public String rollForCharacter(@PathVariable Long characterId,
                                   @RequestParam(required = false) String expression,
                                   @RequestParam(required = false) String quick,
                                   @RequestParam(required = false) String label,
                                   RedirectAttributes redirectAttributes) {
        String toRoll = quick != null ? quick : expression;
        try {
            addResult(redirectAttributes, diceService.rollForCharacter(characterId, toRoll, label), label);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("rollError", e.getMessage());
        }
        return "redirect:/character/" + characterId + "#dice";
    }

    @PostMapping("/campaign/{campaignId}/roll")
    public String rollForCampaign(@PathVariable Long campaignId,
                                  @RequestParam(required = false) String expression,
                                  @RequestParam(required = false) String quick,
                                  @RequestParam(required = false) String label,
                                  RedirectAttributes redirectAttributes) {
        String toRoll = quick != null ? quick : expression;
        try {
            addResult(redirectAttributes, diceService.rollForCampaign(campaignId, toRoll, label), label);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("rollError", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "#dice";
    }

    private static void addResult(RedirectAttributes redirectAttributes, DiceResult result, String label) {
        redirectAttributes.addFlashAttribute("rollResult", result);
        if (label != null && !label.isBlank()) {
            redirectAttributes.addFlashAttribute("rollLabel", label.strip());
        }
    }
}
