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

    /**
     * Строка ленты: сообщение или бросок.
     *
     * @param text текст сообщения; у броска null
     * @param roll бросок; у сообщения null
     */
    public record Entry(Long id, LocalDateTime at, String who, String text, RollLog roll) {
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
     * Последние записи ленты, новые сверху.
     */
    @Transactional(readOnly = true)
    public List<Entry> feed(Long campaignId, int limit) {
        List<Entry> entries = new ArrayList<>();
        chatMessageRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaignId, PageRequest.of(0, limit))
                .forEach(message -> entries.add(new Entry(message.getId(), message.getCreatedAt(), message.getAuthor(), message.getText(), null)));
        rollLogRepository.findByCampaignIdOrderByCreatedAtDescIdDesc(campaignId, PageRequest.of(0, limit))
                .forEach(roll -> entries.add(new Entry(roll.getId(), roll.getCreatedAt(),
                        roll.getCharacter() != null ? roll.getCharacter().getName() : MASTER, null, roll)));
        entries.sort(Comparator.comparing(Entry::at).reversed());
        return entries.size() > limit ? List.copyOf(entries.subList(0, limit)) : entries;
    }
}
