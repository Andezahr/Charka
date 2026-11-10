package app.charka.chat;

import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.ChatMessage;
import app.charka.model.RollLog;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.ChatMessageRepository;
import app.charka.repository.RollLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Чат кампании (U-06): сообщения в одной ленте с бросками — что сказали и что выпало, по времени.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    public static final int TEXT_LENGTH = 500;
    public static final String MASTER = "Мастер";

    private final ChatMessageRepository chatMessageRepository;
    private final RollLogRepository rollLogRepository;
    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;
    private final app.charka.repository.UndoEntryRepository undoEntryRepository;
    private final app.charka.repository.JournalEntryRepository journalEntryRepository;
    private final app.charka.security.AccessService accessService;
    /** Через провайдер: поле боя зависит от сервисов боя */
    private final org.springframework.beans.factory.ObjectProvider<app.charka.map.BattleFieldService> battleFieldService;

    /**
     * Лента для того, кто смотрит: мастеру — со скрытыми бросками, игроку — без них и с «?» вместо врагов в тумане (M-16).
     */
    @Transactional(readOnly = true)
    public List<Entry> feedFor(Campaign campaign, int limit) {
        boolean gm = accessService.isGm(campaign);
        if (gm) {
            return feed(campaign.getId(), limit, true);
        }
        java.util.Set<Long> mine = campaign.getCharacters().stream().filter(accessService::canControl)
                .map(Character::getId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, Boolean> names = battleFieldService.getObject()
                .fogNames(campaign.getId(), new app.charka.map.BattleFieldService.Viewer(mine));
        List<Entry> entries = feed(campaign.getId(), limit, false);
        if (names.isEmpty() || !names.containsValue(true)) {
            return entries;
        }
        java.util.function.UnaryOperator<String> mask = mask(names);
        return entries.stream().map(entry -> masked(entry, mask)).toList();
    }

    /** Имена по убыванию длины одним выражением: «NPC 1» не путается с «NPC» */
    static java.util.function.UnaryOperator<String> mask(java.util.Map<String, Boolean> names) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(names.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(java.util.regex.Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|", "(?<![\\p{L}\\p{N}])(", ")(?![\\p{L}\\p{N}])")));
        return text -> text == null ? null : pattern.matcher(text)
                .replaceAll(match -> Boolean.TRUE.equals(names.get(match.group(1))) ? "?" : java.util.regex.Matcher.quoteReplacement(match.group(1)));
    }

    private static Entry masked(Entry entry, java.util.function.UnaryOperator<String> mask) {
        app.charka.model.JournalEntry event = entry.event();
        if (event != null) {
            app.charka.model.JournalEntry copy = new app.charka.model.JournalEntry();
            copy.setId(event.getId());
            copy.setKind(event.getKind());
            copy.setTitle(mask.apply(event.getTitle()));
            copy.setOutcome(mask.apply(event.getOutcome()));
            copy.setSuccess(event.getSuccess());
            copy.setTotal(event.getTotal());
            copy.setDifficulty(event.getDifficulty());
            copy.setChips(mask.apply(event.getChips()));
            copy.setSteps(mask.apply(event.getSteps()));
            copy.setHidden(event.isHidden());
            copy.setCreatedAt(event.getCreatedAt());
            event = copy;
        }
        return new Entry(entry.id(), entry.at(), mask.apply(entry.who()), mask.apply(entry.text()), entry.roll(), entry.action(), event, entry.undoId());
    }

    /**
     * Строка ленты: сообщение, бросок или действие, которое можно отменить.
     *
     * @param text   текст сообщения или подпись действия; у броска null
     * @param roll   бросок; у сообщения и действия null
     * @param action действие (A-01): id — запись отмены
     */
    public record Entry(Long id, LocalDateTime at, String who, String text, RollLog roll, boolean action,
                        app.charka.model.JournalEntry event, Long undoId) {
        public Entry(Long id, LocalDateTime at, String who, String text, RollLog roll) {
            this(id, at, who, text, roll, false, null, null);
        }

        public Entry(Long id, LocalDateTime at, String who, String text, RollLog roll, boolean action) {
            this(id, at, who, text, roll, action, null, null);
        }

        /** Ключ для браузера: новая запись катит кубик один раз */
        public String key() {
            return (event != null ? "j" : roll != null ? "r" : action ? "a" : "m") + id;
        }
    }

    /** J-01: действие отмены пишется сразу после запроса: в этом окне оно — то же действие, что запись журнала */
    private static final long UNDO_PAIR_SECONDS = 10;

    /**
     * @param characterId кто пишет; null — мастер
     * @throws IllegalArgumentException если текст пуст или длиннее 500, персонаж из другой кампании
     */
    @Transactional
    public ChatMessage post(Long campaignId, Long characterId, String text) {
        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow(NotFoundException.forId("Campaign", campaignId));
        String stripped = text == null ? "" : text.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("Пустое сообщение");
        }
        if (stripped.length() > TEXT_LENGTH) {
            throw new IllegalArgumentException("Сообщение длиннее " + TEXT_LENGTH + " символов");
        }
        ChatMessage message = new ChatMessage();
        message.setCampaign(campaign);
        if (characterId != null) {
            Character character = characterRepository.findById(characterId).orElseThrow(CharacterNotFoundException.forId(characterId));
            if (character.getCampaign() == null || !character.getCampaign().getId().equals(campaignId)) {
                throw new IllegalArgumentException("Персонаж из другой кампании");
            }
            message.setCharacter(character);
            message.setAuthor(character.getName());
        } else {
            message.setAuthor(MASTER);
        }
        message.setText(stripped);
        message.setGameDate(campaign.getCurrentDate());
        message.setCreatedAt(LocalDateTime.now());
        return chatMessageRepository.save(message);
    }

    /**
     * Последние записи ленты, новые сверху. Действия (A-01) — те, что ещё можно отменить.
     */
    @Transactional(readOnly = true)
    public List<Entry> feed(Long campaignId, int limit) {
        return feed(campaignId, limit, false);
    }

    /**
     * @param withHidden мастеру — и скрытые броски (A-09); игроку их нет даже в ответе опроса
     */
    @Transactional(readOnly = true)
    public List<Entry> feed(Long campaignId, int limit, boolean withHidden) {
        List<Entry> entries = new ArrayList<>();
        chatMessageRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaignId, PageRequest.of(0, limit))
                .forEach(message -> entries.add(new Entry(message.getId(), message.getCreatedAt(), message.getAuthor(), message.getText(), null)));
        // J-01: броски, из которых сложилась запись журнала, показаны внутри неё
        (withHidden ? rollLogRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaignId, PageRequest.of(0, limit))
                : rollLogRepository.findByCampaignIdAndHiddenFalseOrderByCreatedAtDescIdDesc(campaignId, PageRequest.of(0, limit)))
                .stream().filter(roll -> roll.getJournalEntryId() == null)
                .forEach(roll -> entries.add(new Entry(roll.getId(), roll.getCreatedAt(),
                        roll.getCharacter() != null ? roll.getCharacter().getName() : MASTER, null, roll)));
        List<app.charka.model.JournalEntry> events = new ArrayList<>(withHidden
                ? journalEntryRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaignId, PageRequest.of(0, limit))
                : journalEntryRepository.findByCampaignIdAndHiddenFalseOrderByCreatedAtDescIdDesc(campaignId, PageRequest.of(0, limit)));
        java.util.Map<Long, Long> undoOf = new java.util.HashMap<>();
        List<app.charka.model.UndoEntry> undos = new ArrayList<>(undoEntryRepository.findByCampaignIdOrderByIdDesc(campaignId));
        java.util.Collections.reverse(undos);
        LocalDateTime previous = null;
        for (app.charka.model.UndoEntry entry : undos) {
            // то же действие уже в журнале — «↶» встаёт на его запись, отдельной строки нет. Запись того же запроса
            // создана после предыдущего действия и до этого
            LocalDateTime after = previous;
            previous = entry.getCreatedAt();
            java.util.Optional<app.charka.model.JournalEntry> same = events.stream()
                    .filter(event -> !undoOf.containsKey(event.getId()))
                    .filter(event -> !event.getCreatedAt().isAfter(entry.getCreatedAt())
                            && (after == null || event.getCreatedAt().isAfter(after))
                            && event.getCreatedAt().isAfter(entry.getCreatedAt().minusSeconds(UNDO_PAIR_SECONDS)))
                    .max(Comparator.comparing(app.charka.model.JournalEntry::getCreatedAt));
            if (same.isPresent()) {
                undoOf.put(same.get().getId(), entry.getId());
                continue;
            }
            String who = entry.getCharacterId() == null ? MASTER
                    : characterRepository.findById(entry.getCharacterId()).map(Character::getName).orElse(MASTER);
            String label = entry.getLabel();
            // подпись начинается с имени персонажа — в ленте оно уже стоит слева
            String text = label.startsWith(who + ": ") ? label.substring(who.length() + 2) : label;
            entries.add(new Entry(entry.getId(), entry.getCreatedAt(), who, text, null, true));
        }
        events.forEach(event -> entries.add(new Entry(event.getId(), event.getCreatedAt(),
                event.getCharacter() != null ? event.getCharacter().getName() : MASTER, null, null, false, event, undoOf.get(event.getId()))));
        entries.sort(Comparator.comparing(Entry::at).reversed());
        return entries.size() > limit ? List.copyOf(entries.subList(0, limit)) : entries;
    }
}
