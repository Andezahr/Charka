package app.charka.controller;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.check.CheckService;
import app.charka.dice.DiceResult;
import app.charka.dice.DiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Броски кубиков и проверки с листа персонажа и со страницы кампании.
 * Бросок — во flash {@code rollResult}/{@code rollLabel}, проверка — {@code checkResult}/{@code rollLabel},
 * непонятный ввод — {@code rollError}.
 */
@Controller
@RequiredArgsConstructor
public class DiceController {

    private final DiceService diceService;
    private final CheckService checkService;

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

    /**
     * Проверка по правилам системы: бросок + {@code modifier} против {@code difficulty} (необязательна).
     */
    @PostMapping("/character/{characterId}/check")
    public String checkForCharacter(@PathVariable Long characterId,
                                    @RequestParam(defaultValue = "0") int modifier,
                                    @RequestParam(required = false) Integer difficulty,
                                    @RequestParam(required = false) String label,
                                    RedirectAttributes redirectAttributes) {
        CheckRequest request = new CheckRequest(label, modifier, difficulty);
        addCheck(redirectAttributes, checkService.checkForCharacter(characterId, request), label);
        return "redirect:/character/" + characterId + "#dice";
    }

    @PostMapping("/campaign/{campaignId}/check")
    public String checkForCampaign(@PathVariable Long campaignId,
                                   @RequestParam(defaultValue = "0") int modifier,
                                   @RequestParam(required = false) Integer difficulty,
                                   @RequestParam(required = false) String label,
                                   RedirectAttributes redirectAttributes) {
        CheckRequest request = new CheckRequest(label, modifier, difficulty);
        addCheck(redirectAttributes, checkService.checkForCampaign(campaignId, request), label);
        return "redirect:/campaign/" + campaignId + "#dice";
    }

    private static void addResult(RedirectAttributes redirectAttributes, DiceResult result, String label) {
        redirectAttributes.addFlashAttribute("rollResult", result);
        addLabel(redirectAttributes, label);
    }

    private static void addCheck(RedirectAttributes redirectAttributes, CheckResult result, String label) {
        redirectAttributes.addFlashAttribute("checkResult", result);
        addLabel(redirectAttributes, label);
    }

    private static void addLabel(RedirectAttributes redirectAttributes, String label) {
        if (label != null && !label.isBlank()) {
            redirectAttributes.addFlashAttribute("rollLabel", label.strip());
        }
    }
}
