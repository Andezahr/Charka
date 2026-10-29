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

    public HealthEvent step(String text) {
        steps.add(text);
        return this;
    }

    public HealthEvent summary(String text) {
        this.summary = text;
        return this;
    }
}
