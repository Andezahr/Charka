package app.charka.undo;

import app.charka.chat.ChatService;
import app.charka.exception.NotFoundException;
import app.charka.model.AppUser;
import app.charka.model.Campaign;
import app.charka.model.ChatMessage;
import app.charka.model.UndoEntry;
import app.charka.repository.CampaignRepository;
import app.charka.repository.ChatMessageRepository;
import app.charka.repository.UndoEntryRepository;
import app.charka.security.AccessService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Отмена последнего действия (A-01). Перед действием игры берётся снимок кампании ({@link CampaignSnapshots}),
 * после — сравнивается: изменилось — снимок сохраняется с подписью. Отменяется только последнее действие кампании,
 * отменить его может тот, кто сделал, или мастер; отменённое уходит, и последним становится предыдущее.
 * Броски в логе остаются, отмена пишется в чат.
 */
@Service
@RequiredArgsConstructor
public class UndoService {

    /** Сколько последних действий кампании можно отменить */
    public static final int KEEP = 20;

    /** Что предложить отменить: последнее действие кампании */
    public record Offer(Long id, Long campaignId, String label) {
    }

    private final CampaignSnapshots snapshots;
    private final UndoEntryRepository undoRepository;
    private final CampaignRepository campaignRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AccessService accessService;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    /** Кампания персонажа; null — персонажа нет или он без кампании */
    @Transactional(readOnly = true)
    public Long campaignOf(Long characterId) {
        List<Long> ids = jdbc.queryForList("SELECT campaign_id FROM character WHERE id = ? AND campaign_id IS NOT NULL", Long.class, characterId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Снимок кампании сейчас; несохранённые изменения JPA сначала записываются, иначе их не видно */
    @Transactional
    public CampaignSnapshots.Snapshot snapshot(long campaignId) {
        entityManager.flush();
        return snapshots.take(campaignId);
    }

    /**
     * Запоминает действие, если оно что-то изменило.
     *
     * @param before снимок до действия
     * @return что предложить отменить; пусто — ничего не изменилось
     */
    @Transactional
    public Optional<Offer> record(CampaignSnapshots.Snapshot before, String label, Long characterId) {
        entityManager.flush();
        if (snapshots.same(before, snapshots.take(before.campaignId()))) {
            return Optional.empty();
        }
        UndoEntry entry = new UndoEntry();
        entry.setCampaignId(before.campaignId());
        entry.setUserId(accessService.current().map(AppUser::getId).orElse(null));
        entry.setCharacterId(characterId);
        entry.setLabel(label);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setSnapshot(snapshots.toJson(before));
        undoRepository.save(entry);
        List<UndoEntry> all = undoRepository.findByCampaignIdOrderByIdDesc(before.campaignId());
        if (all.size() > KEEP) {
            undoRepository.deleteAll(all.subList(KEEP, all.size()));
        }
        return Optional.of(new Offer(entry.getId(), entry.getCampaignId(), entry.getLabel()));
    }

    /** Последнее действие кампании, если его может отменить тот, кто смотрит; иначе null */
    @Transactional(readOnly = true)
    public Offer offer(Long campaignId) {
        if (campaignId == null) {
            return null;
        }
        return undoRepository.findFirstByCampaignIdOrderByIdDesc(campaignId)
                .filter(this::canUndo)
                .map(entry -> new Offer(entry.getId(), entry.getCampaignId(), entry.getLabel()))
                .orElse(null);
    }

    /** Последние действия кампании, новые сверху: для ленты чата */
    @Transactional(readOnly = true)
    public List<UndoEntry> recent(Long campaignId) {
        return undoRepository.findByCampaignIdOrderByIdDesc(campaignId);
    }

    private boolean canUndo(UndoEntry entry) {
        if (!accessService.isEnabled()) {
            return true;
        }
        Campaign campaign = campaignRepository.findById(entry.getCampaignId()).orElse(null);
        if (accessService.isGm(campaign)) {
            return true;
        }
        Long me = accessService.current().map(AppUser::getId).orElse(null);
        return me != null && me.equals(entry.getUserId());
    }

    /**
     * Возвращает кампанию в состояние до действия.
     *
     * @return подпись отменённого
     * @throws IllegalArgumentException если действие уже не последнее, чужое или его персонажа удалили
     */
    @Transactional
    public String undo(Long campaignId, Long entryId) {
        UndoEntry entry = undoRepository.findById(entryId)
                .filter(found -> found.getCampaignId().equals(campaignId))
                .orElseThrow(NotFoundException.forId("UndoEntry", entryId));
        UndoEntry latest = undoRepository.findFirstByCampaignIdOrderByIdDesc(campaignId).orElse(entry);
        if (!latest.getId().equals(entry.getId())) {
            throw new IllegalArgumentException("После этого уже сделали «" + latest.getLabel() + "»: отменяется только последнее действие");
        }
        if (!canUndo(entry)) {
            throw new IllegalArgumentException("Отменить может тот, кто это сделал, или мастер");
        }
        String label = entry.getLabel();
        Long actor = entry.getCharacterId();
        CampaignSnapshots.Snapshot snapshot = snapshots.fromJson(entry.getSnapshot());
        undoRepository.delete(entry);
        entityManager.flush();
        entityManager.clear();
        snapshots.restore(snapshot);
        entityManager.clear();

        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow(NotFoundException.forId("Campaign", campaignId));
        ChatMessage message = new ChatMessage();
        message.setCampaign(campaign);
        message.setAuthor(author(campaign, actor));
        String text = "↶ Отменено: " + label;
        message.setText(text.length() > ChatService.TEXT_LENGTH ? text.substring(0, ChatService.TEXT_LENGTH) : text);
        message.setGameDate(campaign.getCurrentDate());
        message.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(message);
        return label;
    }

    /** Мастер — «Мастер», игрок — его персонаж, чьё действие отменено */
    private String author(Campaign campaign, Long actor) {
        if (!accessService.isEnabled() || accessService.isGm(campaign) || actor == null) {
            return ChatService.MASTER;
        }
        List<String> names = jdbc.queryForList("SELECT name FROM character WHERE id = ?", String.class, actor);
        return names.isEmpty() ? ChatService.MASTER : names.get(0);
    }
}
