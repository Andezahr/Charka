package app.charka.system;

import app.charka.model.Campaign;
import app.charka.model.Character;
import java.util.List;
import java.util.Optional;

/**
 * Время: что происходит за прошедшие дни, заработок, шаблоны регулярных трат и временных эффектов
 * <p>
 * ARC-01: часть правил системы ({@code GameSystem#time()}); каждый метод по умолчанию — «у системы этого нет».
 */
public interface TimeRules {

    /** Система без этой части */
    TimeRules NONE = new TimeRules() {
    };

    /**
     * Вызывается после промотки времени кампании (лечение, траты по правилам системы).
     */
    default List<String> onTimePassed(Campaign campaign, int days) {
        return List.of();
    }

    /**
     * A-10: заработок персонажа, если он работал {@code days} дней; null — у системы нет работы или у персонажа нет роли.
     */
    default Earning earnings(Character character, int days) {
        return null;
    }

    /**
     * Типичные регулярные траты (еда, жильё) для формы на листе. Пусто — только своя трата.
     */
    default List<ExpenseTemplate> getExpenseTemplates() {
        return List.of();
    }

    /**
     * Готовые временные эффекты (препараты, стимуляторы) для блока «Эффекты» на листе. Пусто — только свой эффект.
     */
    default List<app.charka.effect.EffectTemplate> getEffectTemplates() {
        return List.of();
    }

    default Optional<app.charka.effect.EffectTemplate> findEffectTemplate(String code) {
        return getEffectTemplates().stream().filter(template -> template.code().equals(code)).findFirst();
    }
}
