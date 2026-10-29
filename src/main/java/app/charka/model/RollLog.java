package app.charka.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Запись в логе бросков кубиков. Кампания и персонаж необязательны: бросок мастера — без персонажа.
 * Коллекций в Campaign/Character нет намеренно — лог не попадает в JSON-экспорт и не грузится с листом.
 */
@Data
@Entity
public class RollLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Campaign campaign;

    @ManyToOne
    private Character character;

    /** Каноническое выражение: 2d6+3 */
    @Column(nullable = false, length = 64)
    private String expression;

    /** Расшифровка: [3, 5] + 3 = 11 */
    @Column(nullable = false, length = 1000)
    private String details;

    private int total;

    /** Подпись броска («Атака», «Спасбросок»), необязательна */
    private String label;

    /** Игровая дата кампании в момент броска */
    private LocalDate gameDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
