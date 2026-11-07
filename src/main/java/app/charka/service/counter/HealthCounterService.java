package app.charka.service.counter;

import app.charka.repository.WoundRepository;
import app.charka.model.Wound;
import app.charka.system.GameSystemRegistry;
import app.charka.system.GameSystem;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Set;
import app.charka.model.counters.strategy.health.CareOption;
import app.charka.exception.NotFoundException;
import app.charka.skill.SkillService;
import app.charka.skill.SkillCheck;
import app.charka.repository.CharacterRepository;
import app.charka.model.Character;
import app.charka.dto.counter.request.HealthDamageRequestDto;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.CounterStrategy;
import app.charka.model.counters.strategy.CounterStrategyFactory;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.HealthCounterStrategy;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.model.counters.strategy.health.HealthView;
import app.charka.repository.CounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Операции со счётчиками здоровья. Ничего не знает о конкретной игровой системе —
 * всё делегируется {@link HealthCounterStrategy}, найденной по типу счётчика.
 */
@Service
@RequiredArgsConstructor
public class HealthCounterService {

    /** Колонка name — 255 символов */
    private static final int MAX_LOG_TEXT = 255;

    private final CounterService counterService;
    private final CounterRepository counterRepository;
    private final CounterStrategyFactory strategyFactory;
    private final CharacterRepository characterRepository;
    /** Через провайдер: SkillService зависит от реестра систем, а система — от этого сервиса */
    private final ObjectProvider<SkillService> skillService;
    /** Тоже через провайдер: реестр собирает системы, а система CP2020 зависит от этого сервиса */
    private final ObjectProvider<GameSystemRegistry> systemRegistry;
    private final WoundRepository woundRepository;
    private final app.charka.encounter.RoundActions roundActions;
    private final app.charka.journal.JournalService journalService;

    /**
     * J-01: действие со здоровьем — запись журнала «Урон: Олежа», «Спасбросок: Олежа»; внутри атаки — фишки её записи.
     */
    private HealthEvent journaled(Character who, String title, java.util.function.Supplier<HealthEvent> action) {
        return journalService.capture(() -> {
            HealthEvent event = action.get();
            app.charka.journal.JournalService.health(who.getName(), event);
            return event;
        }, event -> event == null ? null : new app.charka.journal.JournalService.Draft(who.getCampaign(), who,
                app.charka.journal.JournalService.Kind.HEALTH, title + ": " + who.getName(), event.getSummary(), null, null, null,
                event.getSteps(), List.of()));
    }

    @Transactional
    public HealthEvent applyDamage(Long characterId, Long counterId, HealthDamageRequestDto requestDto) {
        CounterContext context = contextFor(characterId, counterId);
        return journaled(context.getCounter().getCharacter(), "Урон", () -> applyDamage(context, requestDto));
    }

    private HealthEvent applyDamage(CounterContext context, HealthDamageRequestDto requestDto) {
        DamageRequest request = DamageRequest.builder()
                .amount(requestDto.getAmount())
                .location(requestDto.getLocation())
                .damageType(requestDto.getDamageType())
                .autoSave(requestDto.isAutoSave())
                .extras(requestDto.extras())
                .build();
        int before = context.getCounter().getValue();
        HealthEvent event = strategyFor(context).applyDamage(context, request);
        // урон изнашивает броню — сохраняем и соседние счётчики
        counterRepository.saveAll(context.getSiblings());
        logDamage(context, before, event);
        return event;
    }

    /**
     * Урон персонажу без выбора счётчика: например, от атаки.
     *
     * @return итог; пусто, если у персонажа нет счётчика здоровья
     */
    @Transactional
    public Optional<HealthEvent> applyDamage(Long characterId, DamageRequest request) {
        List<Counter> siblings = counterRepository.findByCharacterId(characterId);
        for (Counter counter : siblings) {
            if (strategyFactory.findStrategy(counter.getType()).orElse(null) instanceof HealthCounterStrategy health) {
                CounterContext context = new CounterContext(counter, siblings);
                if (health.isDead(context)) {
                    return Optional.of(new HealthEvent().step("Цель уже мертва").summary("Цель уже мертва"));
                }
                int before = counter.getValue();
                HealthEvent event = journaled(counter.getCharacter(), "Урон", () -> {
                    HealthEvent applied = health.applyDamage(context, request);
                    counterRepository.saveAll(siblings);
                    logDamage(context, before, applied);
                    return applied;
                });
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    /**
     * Спасбросок персонажа от попадания, без выбора счётчика.
     *
     * @param modifier прибавка к числу спасброска
     * @return итог; пусто, если у персонажа нет счётчика здоровья
     */
    @Transactional
    public Optional<HealthEvent> save(Long characterId, String saveType, int modifier) {
        List<Counter> siblings = counterRepository.findByCharacterId(characterId);
        for (Counter counter : siblings) {
            if (strategyFactory.findStrategy(counter.getType()).orElse(null) instanceof HealthCounterStrategy health) {
                CounterContext context = new CounterContext(counter, siblings);
                if (health.isDead(context)) {
                    return Optional.of(new HealthEvent().step("Цель уже мертва").summary("Цель уже мертва"));
                }
                HealthEvent event = journaled(counter.getCharacter(), "Спасбросок", () -> {
                    HealthEvent saved = health.save(context, saveType, null, modifier);
                    counterRepository.saveAll(siblings);
                    return saved;
                });
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    /**
     * Состояние счётчика здоровья для показа.
     *
     * @param siblings все счётчики персонажа
     * @throws IllegalArgumentException если это не счётчик здоровья
     */
    public HealthView describe(Counter counter, List<Counter> siblings) {
        return strategyFor(new CounterContext(counter, siblings)).describe(new CounterContext(counter, siblings));
    }

    @Transactional
    public HealthEvent heal(Long characterId, Long counterId, int points) {
        CounterContext context = contextFor(characterId, counterId);
        return journaled(context.getCounter().getCharacter(), "Лечение", () -> {
            HealthEvent event = strategyFor(context).heal(context, points);
            counterRepository.save(context.getCounter());
            return event;
        });
    }

    /**
     * @param roll бросок игрока; null — бросает сервер
     */
    @Transactional
    public HealthEvent save(Long characterId, Long counterId, String saveType, Integer roll) {
        return save(characterId, counterId, saveType, roll, 0);
    }

    /** Спасбросок с удачей; удачу тратит вызывающий */
    @Transactional
    public HealthEvent save(Long characterId, Long counterId, String saveType, Integer roll, int luck) {
        CounterContext context = contextFor(characterId, counterId);
        return journaled(context.getCounter().getCharacter(), "Спасбросок", () -> {
            HealthEvent event = strategyFor(context).save(context, saveType, roll, 0, luck);
            counterRepository.save(context.getCounter());
            return event;
        });
    }

    @Transactional
    public HealthEvent stabilize(Long characterId, Long counterId) {
        CounterContext context = contextFor(characterId, counterId);
        return journaled(context.getCounter().getCharacter(), "Стабилизация", () -> {
            HealthEvent event = strategyFor(context).stabilize(context);
            counterRepository.save(context.getCounter());
            return event;
        });
    }

    /**
     * Медпомощь: медик бросает свой навык против состояния пациента, при успехе система меняет здоровье.
     *
     * @param healerId персонаж-медик из той же кампании
     * @throws IllegalArgumentException если помощь не нужна, медик тот же, а себе нельзя, или медик чужой
     */
    @Transactional
    public HealthEvent care(Long characterId, Long counterId, Long healerId, String optionCode, int modifier) {
        return care(characterId, counterId, healerId, optionCode, modifier, 0);
    }

    /** То же с удачей медика: тратится и прибавляется к его броску */
    @Transactional
    public HealthEvent care(Long characterId, Long counterId, Long healerId, String optionCode, int modifier, int luck) {
        CounterContext context = contextFor(characterId, counterId);
        HealthCounterStrategy strategy = strategyFor(context);
        CareOption option = strategy.getCareOptions().stream()
                .filter(candidate -> candidate.code().equals(optionCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Такой помощи нет: " + optionCode));
        if (healerId.equals(characterId) && !option.selfAllowed()) {
            throw new IllegalArgumentException("Себе так помочь нельзя: нужен другой медик");
        }
        Character patient = context.getCounter().getCharacter();
        Character healer = characterRepository.findById(healerId).orElseThrow(NotFoundException.forId("Character", healerId));
        boolean sameCampaign = patient.getCampaign() != null && healer.getCampaign() != null
                && patient.getCampaign().getId().equals(healer.getCampaign().getId());
        if (!sameCampaign) {
            throw new IllegalArgumentException("Медик должен быть из той же кампании");
        }
        Integer difficulty = strategy.careDifficulty(context, option);
        if (difficulty == null) {
            throw new IllegalArgumentException("Сейчас это не нужно");
        }
        return journaled(patient, "Медпомощь", () -> care(context, strategy, option, patient, healer, modifier, difficulty, luck));
    }

    private HealthEvent care(CounterContext context, HealthCounterStrategy strategy, CareOption option, Character patient, Character healer,
                             int modifier, int difficulty, int luck) {
        SkillCheck check = skillService.getObject().check(healer.getId(), option.skillCode(), null, modifier, difficulty, Math.max(0, luck));
        HealthEvent event = new HealthEvent();
        event.chip(Boolean.TRUE.equals(check.result().success()) ? "ok" : "bad", healer.getName() + ": " + (Boolean.TRUE.equals(check.result().success()) ? "помог" : "не помог"));
        event.step(healer.getName() + ", " + check.label() + ": " + check.result().outcome());
        check.result().steps().forEach(step -> event.step("  " + step));
        HealthEvent result = strategy.applyCare(context, option, Boolean.TRUE.equals(check.result().success()));
        result.getSteps().forEach(event::step);
        result.getChips().forEach(chip -> event.chip(chip.kind(), chip.text()));
        counterRepository.save(context.getCounter());
        if (Boolean.TRUE.equals(check.result().success())) {
            log(patient, healer.getName() + ": " + result.getSummary(), null);
        }
        // A-05: медпомощь в бою — действие медика
        roundActions.count(healer);
        return event.summary(result.getSummary());
    }

    /** A-10: какая медпомощь бывает у системы здоровья этого счётчика */
    @Transactional(readOnly = true)
    public List<CareOption> careOptions(Long characterId, Long counterId) {
        return strategyFor(contextFor(characterId, counterId)).getCareOptions();
    }

    /** K-08: выбор отдыха у системы здоровья персонажа; пусто — его нет */
    @Transactional(readOnly = true)
    public List<app.charka.item.ItemField.Option> restOptions(Counter health) {
        return strategyFactory.findStrategy(health.getType()).orElse(null) instanceof HealthCounterStrategy strategy
                ? strategy.getRestOptions() : List.of();
    }

    @Transactional(readOnly = true)
    public String rest(Counter health) {
        return strategyFactory.findStrategy(health.getType()).orElse(null) instanceof HealthCounterStrategy strategy
                ? strategy.getRest(health) : null;
    }

    /** K-08: запомнить, как персонаж отдыхает (при промотке времени) */
    @Transactional
    public void setRest(Long characterId, String rest) {
        healthCounter(characterId).ifPresent(health -> {
            if (strategyFactory.findStrategy(health.getType()).orElse(null) instanceof HealthCounterStrategy strategy
                    && !strategy.getRestOptions().isEmpty()) {
                strategy.setRest(health, rest);
                counterRepository.save(health);
            }
        });
    }

    /** A-10: счётчик здоровья персонажа; пусто — у системы его нет */
    @Transactional(readOnly = true)
    public Optional<Counter> healthCounter(Long characterId) {
        return counterRepository.findByCharacterId(characterId).stream()
                .filter(counter -> strategyFactory.findStrategy(counter.getType()).orElse(null) instanceof HealthCounterStrategy)
                .findFirst();
    }

    /**
     * Кто может оказать помощь: персонажи кампании, сначала с лучшим медицинским навыком, сам пациент — последним.
     */
    @Transactional(readOnly = true)
    public List<Character> healers(Long characterId, List<CareOption> options) {
        Character patient = characterRepository.findById(characterId).orElseThrow(NotFoundException.forId("Character", characterId));
        if (patient.getCampaign() == null) {
            return List.of(patient);
        }
        Set<String> skills = options.stream().map(CareOption::skillCode).collect(Collectors.toSet());
        return patient.getCampaign().getCharacters().stream()
                .sorted(java.util.Comparator.comparing((Character character) -> character.getId().equals(characterId))
                        .thenComparing(character -> -character.getSkills().stream()
                                .filter(skill -> skills.contains(skill.getSkillCode()))
                                .mapToInt(app.charka.model.CharacterSkill::getLevel).max().orElse(0)))
                .toList();
    }

    /**
     * Что выбрать в форме медпомощи по умолчанию: первое нужное сейчас действие (стабилизация раньше лечения), чей навык
     * у медика есть; иначе первое, что не требует спецспособности роли. null — помощь не нужна.
     */
    @Transactional(readOnly = true)
    public String defaultCare(Long characterId, Long counterId, Character healer) {
        CounterContext context = contextFor(characterId, counterId);
        if (!(strategyFactory.getStrategy(context.getCounter().getType()) instanceof HealthCounterStrategy strategy)) {
            return null;
        }
        List<CareOption> needed = strategy.getCareOptions().stream()
                .filter(option -> strategy.careDifficulty(context, option) != null)
                .toList();
        GameSystem system = systemRegistry.getObject().forCharacter(context.getCounter().getCharacter());
        return needed.stream()
                .filter(option -> healer != null && healer.getSkills().stream()
                        .anyMatch(skill -> skill.getSkillCode().equals(option.skillCode()) && skill.getLevel() > 0))
                .findFirst()
                .or(() -> needed.stream()
                        .filter(option -> system.getSkillCatalog().stream()
                                .anyMatch(skill -> skill.code().equals(option.skillCode()) && !skill.special()))
                        .findFirst())
                .map(CareOption::code)
                .orElse(null);
    }

    /**
     * Естественное восстановление всех персонажей кампании за прошедшие дни.
     *
     * @return итоги по каждому счётчику, где что-то произошло
     */
    @Transactional
    public List<String> healOverTime(Long campaignId, int days) {
        List<String> results = new ArrayList<>();
        Map<Long, List<Counter>> byCharacter = counterRepository.findByCharacterCampaignId(campaignId).stream()
                .collect(Collectors.groupingBy(c -> c.getCharacter().getId()));
        for (List<Counter> siblings : byCharacter.values()) {
            for (Counter counter : siblings) {
                CounterStrategy strategy = strategyFactory.findStrategy(counter.getType()).orElse(null);
                if (strategy instanceof HealthCounterStrategy health) {
                    HealthEvent event = health.healOverTime(new CounterContext(counter, siblings), days);
                    if (event.getSummary() != null) {
                        results.add(event.getSummary());
                        log(counter.getCharacter(), event.getSummary(), null);
                    }
                    counterRepository.save(counter);
                }
            }
        }
        return results;
    }

    /**
     * Журнал здоровья ({@link Wound}): урон, который прошёл, и смерть. Броня выдержала — записи нет.
     */
    private void logDamage(CounterContext context, int before, HealthEvent event) {
        Counter counter = context.getCounter();
        int dealt = counter.getValue() - before;
        HealthCounterStrategy strategy = strategyFor(context);
        if (event.getSummary() != null && (dealt > 0 || strategy.isDead(context))) {
            log(counter.getCharacter(), event.getSummary(), dealt > 0 ? (long) dealt : null);
        }
    }

    private void log(Character character, String text, Long severity) {
        if (character == null || text == null) {
            return;
        }
        Wound entry = new Wound();
        entry.setCharacter(character);
        entry.setName(text.length() > MAX_LOG_TEXT ? text.substring(0, MAX_LOG_TEXT - 1) + "…" : text);
        entry.setSeverity(severity);
        entry.setDate(character.getCampaign() != null && character.getCampaign().getCurrentDate() != null
                ? character.getCampaign().getCurrentDate() : java.time.LocalDate.now());
        character.getWounds().add(entry);
        woundRepository.save(entry);
    }

    private CounterContext contextFor(Long characterId, Long counterId) {
        Counter counter = counterService.validateAndGetCounter(characterId, counterId);
        return new CounterContext(counter, counterRepository.findByCharacterId(characterId));
    }

    private HealthCounterStrategy strategyFor(CounterContext context) {
        Counter counter = context.getCounter();
        CounterStrategy strategy = strategyFactory.getStrategy(counter.getType());
        if (strategy instanceof HealthCounterStrategy health) {
            return health;
        }
        throw new IllegalArgumentException("Счётчик '" + counter.getName() + "' не является счётчиком здоровья");
    }
}
