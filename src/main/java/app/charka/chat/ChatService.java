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

    /**
     * Строка ленты: сообщение, бросок или действие, которое можно отменить.
     *
     * @param text   текст сообщения или подпись действия; у броска null
     * @param roll   бросок; у сообщения и действия null
     * @param action действие (A-01): id — запись отмены
     */
    public record Entry(Long id, LocalDateTime at, String who, String text, RollLog roll, boolean action) {
        public Entry(Long id, LocalDateTime at, String who, String text, RollLog roll) {
            this(id, at, who, text, roll, false);
        }
    }

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
        List<Entry> entries = new ArrayList<>();
        chatMessageRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaignId, PageRequest.of(0, limit))
                .forEach(message -> entries.add(new Entry(message.getId(), message.getCreatedAt(), message.getAuthor(), message.getText(), null)));
        rollLogRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaignId, PageRequest.of(0, limit))
                .forEach(roll -> entries.add(new Entry(roll.getId(), roll.getCreatedAt(),
                        roll.getCharacter() != null ? roll.getCharacter().getName() : MASTER, null, roll)));
        undoEntryRepository.findByCampaignIdOrderByIdDesc(campaignId).forEach(entry -> {
            String who = entry.getCharacterId() == null ? MASTER
                    : characterRepository.findById(entry.getCharacterId()).map(Character::getName).orElse(MASTER);
            String label = entry.getLabel();
            // подпись начинается с имени персонажа — в ленте оно уже стоит слева
            String text = label.startsWith(who + ": ") ? label.substring(who.length() + 2) : label;
            entries.add(new Entry(entry.getId(), entry.getCreatedAt(), who, text, null, true));
        });
        entries.sort(Comparator.comparing(Entry::at).reversed());
        return entries.size() > limit ? List.copyOf(entries.subList(0, limit)) : entries;
    }
}
