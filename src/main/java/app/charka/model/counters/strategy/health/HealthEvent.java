package app.charka.model.counters.strategy.health;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Результат действия со здоровьем: короткий итог и пошаговый расчёт для показа игрокам.
 */
@Getter
public class HealthEvent {

    private String summary;
    private final List<String> steps = new ArrayList<>();
    /** J-01: что показать фишками в журнале боя: «4 урона · Торс», «Броня выдержала», «Оглушён» */
    private final List<Chip> chips = new ArrayList<>();

    /**
     * @param kind вид фишки для цвета, см. {@code JournalService.Chip}
     */
    public record Chip(String kind, String text) {
    }

    public HealthEvent chip(String kind, String text) {
        chips.add(new Chip(kind, text));
        return this;
    }

    public HealthEvent step(String text) {
        steps.add(text);
        return this;
    }

    public HealthEvent summary(String text) {
        this.summary = text;
        return this;
    }
}
