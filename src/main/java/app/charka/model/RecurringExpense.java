package app.charka.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Регулярная трата персонажа: аренда, еда. Когда игровое время проходит {@code nextDate}, в деньги пишется расход
 * этой датой, и дата сдвигается на период.
 */
@Getter
@Setter
@Entity
@Table(name = "recurring_expense")
public class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** Сколько списать за период, больше нуля */
    private Long amount;

    @Column(name = "period_days")
    private int periodDays;

    /** Когда следующее списание */
    @Column(name = "next_date")
    private LocalDate nextDate;

    @ManyToOne(optional = false)
    @JsonBackReference
    private Character character;
}
