package app.charka.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Временный эффект персонажа: препарат, стимулятор, последствие. Может править один стат на {@code modifier},
 * снимается сам, когда истёк срок: раунды идут в трекере боя, часы и дни — при промотке времени кампании.
 */
@Getter
@Setter
@Entity
@Table(name = "timed_effect")
public class TimedEffect {

    /** Единица срока. Минуты хранятся раундами: в минуте 20 раундов по 3 секунды */
    public enum Unit {
        ROUND, HOUR, DAY
    }

    public static final int ROUNDS_PER_MINUTE = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** Код стата системы, который правит эффект; null — эффект без числа (заметка со сроком) */
    private String stat;

    private int modifier;

    @Enumerated(EnumType.STRING)
    private Unit unit;

    /** Сколько единиц осталось */
    private int remaining;

    /** Что ещё делает эффект: словами, правила ведёт мастер */
    private String note;

    @ManyToOne(optional = false)
    @JsonBackReference
    private Character character;

    /** «3 раунда», «≈5 мин», «4 ч», «7 дн.» */
    public String getRemainingLabel() {
        return switch (unit) {
            case ROUND -> remaining >= ROUNDS_PER_MINUTE
                    ? "≈" + ((remaining + ROUNDS_PER_MINUTE - 1) / ROUNDS_PER_MINUTE) + " мин"
                    : remaining + " р.";
            case HOUR -> remaining + " ч";
            case DAY -> remaining + " дн.";
        };
    }
}
