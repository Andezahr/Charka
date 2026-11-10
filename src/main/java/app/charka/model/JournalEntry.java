package app.charka.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Запись журнала боя (J-01): одно действие целиком — атака, проверка, противостояние, урон, спасбросок, начало раунда.
 * Как и лог бросков, без коллекции в кампании: не экспортируется и отменой не откатывается.
 */
@Data
@Entity
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @ToString.Exclude
    private Campaign campaign;

    /** Кто действовал; null — мастер или раунд */
    @ManyToOne
    @ToString.Exclude
    private Character character;

    /** {@code journal.JournalService.Kind} */
    @Column(nullable = false, length = 20)
    private String kind;

    /** «Militech Avenger → Бустер 1» */
    @Column(nullable = false, length = 300)
    private String title;

    /** Короткий итог: «Бустер 1 получает 4 урона (Торс). Серьёзное ранение» */
    @Column(length = 1000)
    private String outcome;

    /** Попал, прошёл, победил; null — успеха нет (урон, раунд) */
    private Boolean success;

    /** Итог главного броска и сложность */
    private Integer total;
    private Integer difficulty;

    /** Фишки итога строками «вид:текст» через перевод строки: {@code hit:Попал}, {@code dmg:4 урона · Торс} */
    @Column(length = 2000)
    private String chips;

    /** Расчёт по шагам через перевод строки */
    @Column(columnDefinition = "TEXT")
    private String steps;

    private boolean hidden;

    private LocalDate gameDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** @return фишки {@code {вид, текст}} */
    public List<String[]> chipList() {
        return chips == null || chips.isBlank() ? List.of()
                : Arrays.stream(chips.split("\n")).map(chip -> chip.split(":", 2)).filter(parts -> parts.length == 2).toList();
    }

    public List<String> stepList() {
        return steps == null || steps.isBlank() ? List.of() : List.of(steps.split("\n"));
    }
}
