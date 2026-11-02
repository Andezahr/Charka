package app.charka.controller;

import app.charka.service.RecurringExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Регулярные траты на листе, в блоке денег.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/character/{characterId}/expenses")
public class RecurringExpenseController {

    private static final String BACK = "#money";

    private final RecurringExpenseService expenseService;

    @PostMapping
    public String create(@PathVariable Long characterId, @RequestParam String name, @RequestParam long amount,
                         @RequestParam int periodDays, RedirectAttributes redirectAttributes) {
        try {
            expenseService.create(characterId, name, amount, periodDays);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/character/" + characterId + BACK;
    }

    @PostMapping("/{expenseId}/delete")
    public String delete(@PathVariable Long characterId, @PathVariable Long expenseId) {
        expenseService.delete(characterId, expenseId);
        return "redirect:/character/" + characterId + BACK;
    }
}
