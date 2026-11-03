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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Сообщение в чате кампании (U-06). Как лог бросков — без коллекции в {@link Campaign}: не грузится с кампанией и не экспортируется.
 */
@Getter
@Setter
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    /** Кто пишет; null — мастер или персонаж удалён */
    @ManyToOne
    @JoinColumn(name = "character_id")
    private Character character;

    /** Имя на момент сообщения: персонажа могут переименовать или удалить */
    private String author;

    private String text;

    @Column(name = "game_date")
    private LocalDate gameDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
