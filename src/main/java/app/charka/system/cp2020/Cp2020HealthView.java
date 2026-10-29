package app.charka.system.cp2020;

import app.charka.model.counters.strategy.health.HealthView;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Всё, что нужно шаблону counters/types/cp2020-health для карточки здоровья.
 */
@Value
@Builder
public class Cp2020HealthView implements HealthView {

    int damage;
    int maxDamage;
    String statusLabel;
    /** ok / light / serious / critical / mortal / dead */
    String statusCss;
    boolean dead;
    boolean stunned;
    boolean stabilized;
    boolean deathSaveRequired;

    int body;
    int btm;
    int stunTarget;
    /** null, если спасбросок от смерти сейчас не нужен */
    Integer deathTarget;
    /** Штрафы от ран одной строкой; null — штрафов нет */
    String penalty;

    List<TrackLevel> track;
    List<Option> lostLimbs;
    List<Option> locations;
    List<Option> damageTypes;

    @Value
    public static class TrackLevel {
        String name;
        String css;
        /** Сколько из 4 клеток уровня заполнено */
        int filled;
    }

    @Value
    public static class Option {
        String code;
        String label;
    }
}
