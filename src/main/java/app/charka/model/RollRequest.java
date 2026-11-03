package app.charka.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Запрос броска от мастера (A-07): «Олежа, Внимательность 15». Игрок видит карточку с одной кнопкой, пока не бросит.
 * Несколько запросов одной проверки (A-08) — общий {@code groupKey}: когда брошены все, в ленту уходит таблица.
 */
@Getter
@Setter
@Entity
@Table(name = "roll_request")
public class RollRequest {

    /** Проверка навыка из каталога системы */
    public static final String SKILL = "SKILL";
    /** Спасбросок системы (CP2020: оглушение, смерть) */
    public static final String SAVE = "SAVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private Character character;

    /** {@link #SKILL} или {@link #SAVE} */
    private String kind;

    /** Код навыка или спасброска */
    private String code;

    /** «Внимательность 15» */
    private String label;

    /** Сложность проверки; null — без сложности, у спасброска своя */
    private Integer difficulty;

    @Column(name = "group_key")
    private String groupKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Когда брошен; null — ещё ждёт */
    @Column(name = "done_at")
    private LocalDateTime doneAt;

    /** Прошёл ли; null — без сложности */
    private Boolean success;

    private Integer total;

    /** Итог строкой для мастера и ленты */
    private String result;

    public boolean isOpen() {
        return doneAt == null;
    }
}
