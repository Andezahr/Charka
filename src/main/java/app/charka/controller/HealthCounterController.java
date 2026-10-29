package app.charka.controller;

import app.charka.dto.counter.request.HealthDamageRequestDto;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.service.counter.HealthCounterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.function.Supplier;

/**
 * Действия со счётчиками здоровья. Итог показывается flash-сообщением successMessage,
 * пошаговый расчёт — списком healthSteps.
 */
@Controller
@RequestMapping("/character/{characterId}/counters/{counterId}/health")
@RequiredArgsConstructor
public class HealthCounterController {

    private final HealthCounterService healthCounterService;

    @PostMapping("/damage")
    public String damage(@PathVariable Long characterId,
                         @PathVariable Long counterId,
                         @Valid @ModelAttribute HealthDamageRequestDto requestDto,
                         BindingResult result,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка валидации: " + result.getAllErrors().getFirst().getDefaultMessage());
            return redirectToCounters(characterId);
        }
        return perform(characterId, redirectAttributes, "Ошибка нанесения урона",
                () -> healthCounterService.applyDamage(characterId, counterId, requestDto));
    }

    @PostMapping("/heal")
    public String heal(@PathVariable Long characterId,
                       @PathVariable Long counterId,
                       @RequestParam Integer points,
                       RedirectAttributes redirectAttributes) {
        return perform(characterId, redirectAttributes, "Ошибка лечения",
                () -> healthCounterService.heal(characterId, counterId, points));
    }

    @PostMapping("/save/{saveType}")
    public String save(@PathVariable Long characterId,
                       @PathVariable Long counterId,
                       @PathVariable String saveType,
                       @RequestParam(required = false) Integer roll,
                       RedirectAttributes redirectAttributes) {
        return perform(characterId, redirectAttributes, "Ошибка спасброска",
                () -> healthCounterService.save(characterId, counterId, saveType, roll));
    }

    @PostMapping("/stabilize")
    public String stabilize(@PathVariable Long characterId,
                            @PathVariable Long counterId,
                            RedirectAttributes redirectAttributes) {
        return perform(characterId, redirectAttributes, "Ошибка стабилизации",
                () -> healthCounterService.stabilize(characterId, counterId));
    }

    private String perform(Long characterId, RedirectAttributes redirectAttributes,
                           String errorPrefix, Supplier<HealthEvent> action) {
        try {
            HealthEvent event = action.get();
            redirectAttributes.addFlashAttribute("successMessage", event.getSummary());
            redirectAttributes.addFlashAttribute("healthSteps", event.getSteps());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", errorPrefix + ": " + e.getMessage());
        }
        return redirectToCounters(characterId);
    }

    private String redirectToCounters(Long characterId) {
        return "redirect:/character/" + characterId + "/counters";
    }
}
