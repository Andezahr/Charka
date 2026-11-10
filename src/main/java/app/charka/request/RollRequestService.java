package app.charka.request;

import app.charka.chat.ChatService;
import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.ChatMessage;
import app.charka.model.RollRequest;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.repository.CampaignRepository;
import app.charka.repository.ChatMessageRepository;
import app.charka.repository.RollRequestRepository;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.SkillCheck;
import app.charka.skill.SkillDefinition;
import app.charka.skill.SkillService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Запросы бросков от мастера (A-07) и групповая проверка (A-08). Мастер выбирает, что бросать (навык каталога системы
 * со сложностью или спасбросок), и кого; игрок видит карточку и жмёт одну кнопку — всё уже подставлено. NPC мастер бросает
 * сразу, «Бросить за всех» — тоже. Брошены все из группы — в ленту одной строкой: кто прошёл, кто нет, на сколько.
 */
@Service
@RequiredArgsConstructor
public class RollRequestService {

    public static final String SKILL_PREFIX = "skill:";
    public static final String SAVE_PREFIX = "save:";
    /** KF-10: простая проверка: «check:» и подпись мастера («Взлом замка») */
    public static final String CHECK_PREFIX = "check:";
    private static final String CHECK_LABEL = "Проверка";

    /** Что можно попросить: значение для формы и подпись */
    public record Choice(String value, String label) {
    }

    private final RollRequestRepository requestRepository;
    private final CampaignRepository campaignRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GameSystemRegistry systemRegistry;
    private final SkillService skillService;
    private final HealthCounterService healthCounterService;
    private final app.charka.check.CheckService checkService;

    /**
     * Спасброски системы, потом навыки, которые в кампании уже просили (частые первыми), потом остальные по алфавиту;
     * свои навыки (языки) не предлагаются.
     * KF-10: в системе без навыков (Космозэки, «Без системы») первой идёт простая проверка — иначе просить было нечего.
     */
    @Transactional(readOnly = true)
    public List<Choice> choices(Long campaignId) {
        GameSystem system = systemRegistry.get(campaign(campaignId).getSystem());
        List<Choice> choices = new ArrayList<>();
        if (plainChecks(system)) {
            choices.add(new Choice(CHECK_PREFIX, CHECK_LABEL));
        }
        system.getRequestableSaves().forEach(save -> choices.add(new Choice(SAVE_PREFIX + save.value(), save.label())));
        // UX-21a: то, что в этой кампании уже просили (до пяти навыков), — сразу после спасбросков, остальное по алфавиту
        List<String> frequent = requestRepository.frequentSkillCodes(campaignId).stream().limit(5).toList();
        java.util.Map<String, SkillDefinition> byCode = new java.util.HashMap<>();
        system.getSkillCatalog().stream().filter(skill -> !skill.custom()).forEach(skill -> byCode.put(skill.code(), skill));
        frequent.stream().map(byCode::get).filter(java.util.Objects::nonNull)
                .forEach(skill -> choices.add(new Choice(SKILL_PREFIX + skill.code(), skill.name())));
        system.getSkillCatalog().stream()
                .filter(skill -> !skill.custom() && !frequent.contains(skill.code()))
                .sorted(Comparator.comparing(SkillDefinition::name))
                .forEach(skill -> choices.add(new Choice(SKILL_PREFIX + skill.code(), skill.name())));
        return choices;
    }

    /** Открытые запросы кампании, старые сверху */
    /** KF-10: просить простую проверку — где нет каталога навыков */
    public static boolean plainChecks(GameSystem system) {
        return system.getSkillCatalog().isEmpty();
    }

    /** KF-10: что проверяли — подпись без сложности в конце */
    private static String subjectOf(RollRequest request) {
        String label = request.getLabel();
        String tail = request.getDifficulty() == null ? null : " " + request.getDifficulty();
        return tail != null && label.endsWith(tail) ? label.substring(0, label.length() - tail.length()) : label;
    }

    /** KF-10: в форме спрашивать «Что проверяем» */
    @Transactional(readOnly = true)
    public boolean plainChecks(Long campaignId) {
        return plainChecks(systemRegistry.get(campaign(campaignId).getSystem()));
    }

    @Transactional(readOnly = true)
    public List<RollRequest> open(Long campaignId) {
        return requestRepository.findByCampaignIdAndDoneAtIsNullOrderByIdAsc(campaignId);
    }

    /**
     * Попросить бросок. NPC и при {@code rollNow} бросается сразу.
     *
     * @param what       «skill:код» или «save:код»
     * @param difficulty сложность навыка; у спасброска не нужна
     * @return итоги брошенных сразу
     * @throws IllegalArgumentException неизвестный навык, никого не выбрали, персонаж из другой кампании
     */
    @Transactional
    public List<String> ask(Long campaignId, List<Long> characterIds, String what, Integer difficulty, boolean rollNow) {
        return ask(campaignId, characterIds, what, difficulty, rollNow, false);
    }

    /**
     * @param hideNpcs A-09: броски NPC скрыты от игроков и не входят в общую строку группы
     */
    @Transactional
    public List<String> ask(Long campaignId, List<Long> characterIds, String what, Integer difficulty, boolean rollNow, boolean hideNpcs) {
        return ask(campaignId, characterIds, what, difficulty, rollNow, hideNpcs, null);
    }

    /**
     * @param subject KF-10: что проверяем — подпись простой проверки («Взлом замка»); у навыка и спасброска не нужна
     */
    @Transactional
    public List<String> ask(Long campaignId, List<Long> characterIds, String what, Integer difficulty, boolean rollNow, boolean hideNpcs,
                            String subject) {
        Campaign campaign = campaign(campaignId);
        if (characterIds == null || characterIds.isEmpty()) {
            throw new IllegalArgumentException("Выберите, кто бросает");
        }
        Choice choice = choices(campaignId).stream().filter(candidate -> candidate.value().equals(what)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Такой проверки нет: " + what));
        boolean plain = what.startsWith(CHECK_PREFIX);
        boolean skill = what.startsWith(SKILL_PREFIX);
        String title = plain && subject != null && !subject.isBlank() ? subject.strip() : choice.label();
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }
        String groupKey = characterIds.size() > 1 ? UUID.randomUUID().toString().replace("-", "").substring(0, 16) : null;
        List<RollRequest> created = new ArrayList<>();
        for (Long characterId : characterIds) {
            Character character = campaign.getCharacters().stream().filter(candidate -> candidate.getId().equals(characterId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Такого персонажа в кампании нет"));
            RollRequest request = new RollRequest();
            request.setCampaignId(campaignId);
            request.setCharacter(character);
            request.setKind(plain ? RollRequest.CHECK : skill ? RollRequest.SKILL : RollRequest.SAVE);
            request.setCode(plain ? RollRequest.CHECK : what.substring(what.indexOf(':') + 1));
            request.setDifficulty(skill || plain ? difficulty : null);
            request.setLabel(title + ((skill || plain) && difficulty != null ? " " + difficulty : ""));
            // скрытый NPC в общую строку группы не попадает: она видна всем
            request.setGroupKey(hideNpcs && character.isNpc() ? null : groupKey);
            request.setCreatedAt(LocalDateTime.now());
            created.add(requestRepository.save(request));
        }
        List<String> results = new ArrayList<>();
        for (RollRequest request : created) {
            if (rollNow || request.getCharacter().isNpc()) {
                boolean hidden = hideNpcs && request.getCharacter().isNpc();
                results.add((hidden ? "🙈 " : "") + request.getCharacter().getName() + ": "
                        + app.charka.dice.HiddenRolls.run(hidden, () -> perform(request)));
            }
        }
        return results;
    }

    /**
     * Бросить по запросу: игрок своим нажатием или мастер за него. Права проверяет вызывающий.
     *
     * @return итог строкой
     * @throws IllegalArgumentException запрос уже брошен
     */
    @Transactional
    public String roll(Long campaignId, Long requestId) {
        return perform(get(campaignId, requestId));
    }

    @Transactional(readOnly = true)
    public RollRequest get(Long campaignId, Long requestId) {
        return requestRepository.findById(requestId)
                .filter(request -> request.getCampaignId().equals(campaignId))
                .orElseThrow(NotFoundException.forId("RollRequest", requestId));
    }

    /** Мастер передумал */
    @Transactional
    public void cancel(Long campaignId, Long requestId) {
        RollRequest request = get(campaignId, requestId);
        requestRepository.delete(request);
        if (request.getGroupKey() != null) {
            summarize(request.getGroupKey());
        }
    }

    private String perform(RollRequest request) {
        if (!request.isOpen()) {
            throw new IllegalArgumentException("Уже брошено: " + request.getResult());
        }
        Character character = request.getCharacter();
        if (RollRequest.CHECK.equals(request.getKind())) {
            // KF-10: кубик проверки вне боя (Космозэки: 1к12) против сложности мастера; подпись в журнале — что проверяли
            app.charka.check.CheckResult result = checkService.tableCheckForCharacter(character.getId(),
                    new app.charka.check.CheckRequest(subjectOf(request), 0, request.getDifficulty()));
            request.setTotal(result.total());
            request.setSuccess(result.success());
            request.setResult(request.getLabel() + ": " + result.total()
                    + (result.success() == null ? "" : result.success() ? " — успех" : " — провал"));
        } else if (RollRequest.SKILL.equals(request.getKind())) {
            SkillCheck check = skillService.check(character.getId(), request.getCode(), null, 0, request.getDifficulty());
            request.setTotal(check.result().total());
            request.setSuccess(check.result().success());
            request.setResult(request.getLabel() + ": " + check.result().total()
                    + (check.result().success() == null ? "" : check.result().success() ? " — успех" : " — провал"));
        } else {
            HealthEvent event = healthCounterService.save(character.getId(), request.getCode(), 0)
                    .orElseThrow(() -> new IllegalArgumentException("У персонажа нет здоровья для спасброска"));
            request.setResult(request.getLabel() + ": " + event.getSummary());
            // спасбросок в лог бросков не пишется: итог — сообщением в ленту
            post(request.getCampaignId(), character, character.getName(), "🎲 " + request.getResult());
        }
        request.setDoneAt(LocalDateTime.now());
        if (request.getGroupKey() != null) {
            summarize(request.getGroupKey());
        }
        return request.getResult();
    }

    /** Группа брошена целиком — одна строка в ленту: «Внимательность 15: Колясик ✓ 17 (+2) · Олежа ✗ 12 (−3)» */
    private void summarize(String groupKey) {
        List<RollRequest> group = requestRepository.findByGroupKeyOrderByIdAsc(groupKey);
        if (group.isEmpty() || group.stream().anyMatch(RollRequest::isOpen)) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (RollRequest request : group) {
            String mark = request.getSuccess() == null ? "" : request.getSuccess() ? " ✓" : " ✗";
            String margin = request.getTotal() != null && request.getDifficulty() != null
                    ? " (" + signed(request.getTotal() - request.getDifficulty()) + ")" : "";
            String value = request.getTotal() != null ? " " + request.getTotal() + margin
                    : " " + request.getResult().substring(request.getResult().indexOf(':') + 1).strip();
            parts.add(request.getCharacter().getName() + mark + value);
        }
        RollRequest first = group.getFirst();
        post(first.getCampaignId(), null, ChatService.MASTER, "Проверка " + first.getLabel() + ": " + String.join(" · ", parts));
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : value < 0 ? "−" + Math.abs(value) : "0";
    }

    private void post(Long campaignId, Character character, String author, String text) {
        Campaign campaign = campaign(campaignId);
        ChatMessage message = new ChatMessage();
        message.setCampaign(campaign);
        message.setCharacter(character);
        message.setAuthor(author);
        message.setText(text.length() > ChatService.TEXT_LENGTH ? text.substring(0, ChatService.TEXT_LENGTH) : text);
        message.setGameDate(campaign.getCurrentDate());
        message.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(message);
    }

    private Campaign campaign(Long campaignId) {
        return campaignRepository.findById(campaignId).orElseThrow(NotFoundException.forId("Campaign", campaignId));
    }
}
