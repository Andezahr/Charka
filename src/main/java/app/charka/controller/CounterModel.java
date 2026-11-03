package app.charka.controller;

import app.charka.dto.counter.request.CounterCreateRequestDto;
import app.charka.dto.counter.response.CounterResponseDto;
import app.charka.model.counters.strategy.health.CareOption;
import app.charka.model.counters.strategy.health.HealthCounterStrategy;
import app.charka.model.counters.strategy.CounterStrategyFactory;
import app.charka.service.counter.CounterService;
import app.charka.service.counter.HealthCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;

/**
 * R-05: что нужно карточкам счётчиков — на листе персонажа (шапка состояния и «Счётчики») и на прежней странице счётчиков.
 */
@Component
@RequiredArgsConstructor
class CounterModel {

    private final CounterService counterService;
    private final HealthCounterService healthCounterService;
    private final CounterStrategyFactory strategyFactory;

    /** Счётчики, типы для формы, медики и выбор помощи по умолчанию, пустая форма нового счётчика */
    List<CounterResponseDto> fill(Long characterId, Model model) {
        List<CounterResponseDto> counters = counterService.getAllCountersByCharacterId(characterId);
        model.addAttribute("counters", counters);
        model.addAttribute("characterId", characterId);
        model.addAttribute("counterTypes", counterService.getCounterTypes(characterId));
        List<app.charka.model.Character> healers = healthCounterService.healers(characterId, careOptions());
        model.addAttribute("healers", healers);
        counters.stream()
                .filter(counter -> counter.getView() instanceof app.charka.model.counters.strategy.health.HealthView)
                .findFirst()
                .ifPresent(health -> model.addAttribute("defaultCare",
                        healthCounterService.defaultCare(characterId, health.getId(), healers.isEmpty() ? null : healers.get(0))));
        CounterCreateRequestDto createRequest = new CounterCreateRequestDto();
        createRequest.setCharacterId(characterId);
        createRequest.setCounterType("SIMPLE_INTEGER");
        model.addAttribute("createRequest", createRequest);
        return counters;
    }

    private List<CareOption> careOptions() {
        return strategyFactory.getAllStrategies().stream()
                .filter(HealthCounterStrategy.class::isInstance)
                .map(HealthCounterStrategy.class::cast)
                .flatMap(strategy -> strategy.getCareOptions().stream())
                .toList();
    }
}
