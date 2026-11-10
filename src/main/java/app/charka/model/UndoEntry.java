package app.charka.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Отмена (A-01): снимок игрового состояния кампании до действия. Как лог бросков — без коллекции в {@link Campaign},
 * в экспорт не идёт. Хранятся последние {@code UndoService.KEEP} на кампанию.
 */
@Getter
@Setter
@Entity
@Table(name = "undo_entry")
public class UndoEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /** Кто сделал; null — пользователей нет */
    @Column(name = "user_id")
    private Long userId;

    /** Чьё действие; null — действие кампании (ход боя, промотка) */
    @Column(name = "character_id")
    private Long characterId;

    /** «Колясик: выстрел — Militech Avenger» */
    private String label;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** JSON {@code CampaignSnapshot} */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String snapshot;
}
