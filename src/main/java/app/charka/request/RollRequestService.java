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

    /** Что можно попросить: значение для формы и подпись */
    public record Choice(String value, String label) {
    }

    private final RollRequestRepository requestRepository;
    private final CampaignRepository campaignRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GameSystemRegistry systemRegistry;
    private final SkillService skillService;
    private final HealthCounterService healthCounterService;

    /** Спасброски системы, потом навыки каталога по алфавиту; свои навыки (языки) не предлагаются */
    @Transactional(readOnly = true)
    public List<Choice> choices(Long campaignId) {
        GameSystem system = systemRegistry.get(campaign(campaignId).getSystem());
        List<Choice> choices = new ArrayList<>();
        system.getRequestableSaves().forEach(save -> choices.add(new Choice(SAVE_PREFIX + save.value(), save.label())));
        system.getSkillCatalog().stream()
                .filter(skill -> !skill.custom())
                .sorted(Comparator.comparing(SkillDefinition::name))
                .forEach(skill -> choices.add(new Choice(SKILL_PREFIX + skill.code(), skill.name())));
        return choices;
    }

    /** Открытые запросы кампании, старые сверху */
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
        Campaign campaign = campaign(campaignId);
        if (characterIds == null || characterIds.isEmpty()) {
            throw new IllegalArgumentException("Выберите, кто бросает");
        }
        Choice choice = choices(campaignId).stream().filter(candidate -> candidate.value().equals(what)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Такой проверки нет: " + what));
        boolean skill = what.startsWith(SKILL_PREFIX);
        String groupKey = characterIds.size() > 1 ? UUID.randomUUID().toString().replace("-", "").substring(0, 16) : null;
        List<RollRequest> created = new ArrayList<>();
        for (Long characterId : characterIds) {
            Character character = campaign.getCharacters().stream().filter(candidate -> candidate.getId().equals(characterId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Такого персонажа в кампании нет"));
            RollRequest request = new RollRequest();
            request.setCampaignId(campaignId);
            request.setCharacter(character);
            request.setKind(skill ? RollRequest.SKILL : RollRequest.SAVE);
            request.setCode(what.substring(what.indexOf(':') + 1));
            request.setDifficulty(skill ? difficulty : null);
            request.setLabel(choice.label() + (skill && difficulty != null ? " " + difficulty : ""));
            request.setGroupKey(groupKey);
            request.setCreatedAt(LocalDateTime.now());
            created.add(requestRepository.save(request));
        }
        List<String> results = new ArrayList<>();
        for (RollRequest request : created) {
            if (rollNow || request.getCharacter().isNpc()) {
                results.add(request.getCharacter().getName() + ": " + perform(request));
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
        if (RollRequest.SKILL.equals(request.getKind())) {
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
