package app.charka.controller;

import app.charka.dto.counter.request.CounterAttributesRequestDto;
import app.charka.dto.counter.request.CounterCreateRequestDto;
import app.charka.dto.counter.request.CounterUpdateRequestDto;
import app.charka.dto.counter.response.CounterResponseDto;
import app.charka.service.counter.CounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.util.List;

@Controller
@RequestMapping("/character/{characterId}/counters")
@RequiredArgsConstructor
public class CounterController {

    private final CounterService counterService;
    private final app.charka.service.counter.HealthCounterService healthCounterService;
    private final app.charka.model.counters.strategy.CounterStrategyFactory strategyFactory;

    @GetMapping
    public String listCounters(@PathVariable Long characterId, Model model) {
        List<CounterResponseDto> counters = counterService.getAllCountersByCharacterId(characterId);
        model.addAttribute("counters", counters);
        model.addAttribute("characterId", characterId);
        model.addAttribute("counterTypes", counterService.getCounterTypes(characterId));
        List<app.charka.model.Character> healers = healthCounterService.healers(characterId, careOptions());
        model.addAttribute("healers", healers);
        // R-01: шапке нужен сам персонаж; он есть среди тех, кто может лечить (сам себя — последним)
        healers.stream().filter(healer -> characterId.equals(healer.getId())).findFirst()
                .ifPresent(character -> model.addAttribute("character", character));
        counters.stream()
                .filter(counter -> counter.getView() instanceof app.charka.model.counters.strategy.health.HealthView)
                .findFirst()
                .ifPresent(health -> model.addAttribute("defaultCare",
                        healthCounterService.defaultCare(characterId, health.getId(), healers.isEmpty() ? null : healers.get(0))));

        // Создаем DTO и предзаполняем characterId
        CounterCreateRequestDto createRequest = new CounterCreateRequestDto();
        createRequest.setCharacterId(characterId);
        createRequest.setCounterType("SIMPLE_INTEGER");
        model.addAttribute("createRequest", createRequest);

        return "counters/list";
    }

    @PostMapping("/{counterId}/update")
    public String incrementCounter(@PathVariable Long characterId,
                                   @PathVariable Long counterId,
                                   @RequestParam(defaultValue = "1") Integer value,
                                   RedirectAttributes redirectAttributes) {
        try {
            CounterUpdateRequestDto requestDto = new CounterUpdateRequestDto();
            requestDto.setValue(value);

            CounterResponseDto response = counterService.updateCounter(characterId, counterId, requestDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Счётчик '" + response.getName() + "' увеличен на " + value + ". Новое значение: " + response.getValue());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка увеличения счётчика: " + e.getMessage());
        }
        return "redirect:/character/" + characterId + "/counters";
    }


    @PostMapping("/{counterId}/reset")
    public String resetCounter(@PathVariable Long characterId,
                               @PathVariable Long counterId,
                               RedirectAttributes redirectAttributes) {
        try {
            CounterResponseDto response = counterService.resetCounter(characterId, counterId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Счётчик '" + response.getName() + "' сброшен. Значение: " + response.getValue());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка сброса счётчика: " + e.getMessage());
        }
        return "redirect:/character/" + characterId + "/counters";
    }

    @PostMapping("/{counterId}/set")
    public String setCounterValue(@PathVariable Long characterId,
                                  @PathVariable Long counterId,
                                  @RequestParam Integer setValue,
                                  RedirectAttributes redirectAttributes) {
        try {
            CounterUpdateRequestDto requestDto = new CounterUpdateRequestDto();
            requestDto.setValue(setValue);

            CounterResponseDto response = counterService.setCounterValue(characterId, counterId, requestDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Счётчик '" + response.getName() + "' установлен в значение: " + response.getValue());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка установки значения: " + e.getMessage());
        }
        return "redirect:/character/" + characterId + "/counters";
    }

    @PostMapping("/create")
    public String createCounter(@PathVariable Long characterId,
                                @Valid @ModelAttribute("createRequest") CounterCreateRequestDto requestDto,
                                BindingResult result,
                                RedirectAttributes redirectAttributes) {

        // Убеждаемся, что characterId установлен в DTO
        requestDto.setCharacterId(characterId);

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка валидации: " + result.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/character/" + characterId + "/counters";
        }

        try {
            CounterResponseDto response = counterService.createCounter(requestDto);
            String message = "Счётчик '" + response.getName() + "' создан";
            if (!response.getCreatedWith().isEmpty()) {
                message += ". Вместе с ним созданы обязательные: " + String.join(", ", response.getCreatedWith());
            }
            redirectAttributes.addFlashAttribute("successMessage", message);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка создания счётчика: " + e.getMessage());
        }
        return "redirect:/character/" + characterId + "/counters";
    }

    /**
     * Правка attributes из формы карточки: поля вида attributes[body], attributes[sp_HEAD].
     */
    @PostMapping("/{counterId}/attributes")
    public String updateAttributes(@PathVariable Long characterId,
                                   @PathVariable Long counterId,
                                   @ModelAttribute CounterAttributesRequestDto requestDto,
                                   RedirectAttributes redirectAttributes) {
        try {
            CounterResponseDto response = counterService.updateAttributes(characterId, counterId, requestDto.getAttributes());
            redirectAttributes.addFlashAttribute("successMessage", "Счётчик '" + response.getName() + "' обновлён");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка изменения счётчика: " + e.getMessage());
        }
        return "redirect:/character/" + characterId + "/counters";
    }

    /**
     * Особое действие типа счётчика (например, ремонт брони).
     */
    @PostMapping("/{counterId}/action/{action}")
    public String performAction(@PathVariable Long characterId,
                                @PathVariable Long counterId,
                                @PathVariable String action,
                                RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("successMessage",
                    counterService.performAction(characterId, counterId, action));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
        }
        return "redirect:/character/" + characterId + "/counters";
    }

    @DeleteMapping("/{counterId}")
    public String deleteCounter(@PathVariable Long characterId,
                                @PathVariable Long counterId,
                                RedirectAttributes redirectAttributes) {
        try {
            counterService.deleteCounter(characterId, counterId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Счётчик успешно удален");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка удаления счётчика: " + e.getMessage());
        }
        return "redirect:/character/" + characterId + "/counters";
    }

    /** Медпомощь всех систем здоровья: для порядка медиков, лучший по этим навыкам — первым */
    private List<app.charka.model.counters.strategy.health.CareOption> careOptions() {
        return strategyFactory.getAllStrategies().stream()
                .filter(strategy -> strategy instanceof app.charka.model.counters.strategy.health.HealthCounterStrategy)
                .flatMap(strategy -> ((app.charka.model.counters.strategy.health.HealthCounterStrategy) strategy).getCareOptions().stream())
                .toList();
    }
}
