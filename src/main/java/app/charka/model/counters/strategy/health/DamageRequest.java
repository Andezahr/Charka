package app.charka.model.counters.strategy.health;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Входящий урон. Поля, которые конкретной системе не нужны (зона попадания в D&D), она игнорирует.
 */
@Value
@Builder
public class DamageRequest {
    /** «Сырой» урон оружия, до брони */
    int amount;
    /** Код зоны попадания; null — система бросает сама */
    String location;
    /** Код типа урона (обычный, бронебойный…); null — тип по умолчанию */
    String damageType;
    /** Сразу бросить проверки, положенные после ранения (в CP2020 — спасбросок от оглушения) */
    @Builder.Default
    boolean autoSave = true;
    /** Особенности правил системы: ядро их только передаёт */
    @Builder.Default
    Map<String, Integer> extras = Map.of();

    public int extra(String key, int defaultValue) {
        return extras.getOrDefault(key, defaultValue);
    }
}
