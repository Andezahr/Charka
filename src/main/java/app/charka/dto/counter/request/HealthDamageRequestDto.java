package app.charka.dto.counter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HealthDamageRequestDto {

    @NotNull(message = "Укажите урон")
    @Min(value = 0, message = "Урон не может быть отрицательным")
    private Integer amount;

    /** Пусто — зона попадания бросается случайно */
    private String location;

    private String damageType;

    /** Чекбокс; в форме рядом лежит скрытое поле _autoSave, чтобы снятая галочка давала false */
    private boolean autoSave = true;

    /** K-03: сколько попаданий этим уроном (Космозэки: броня вычитается из каждого); пусто — одно */
    private Integer hits;

    /** K-03: броня вдвое меньше — оружие ближнего боя (Космозэки) */
    private boolean armorHalf;

    /** Особенности системы для {@code DamageRequest.extras} */
    public java.util.Map<String, Integer> extras() {
        java.util.Map<String, Integer> extras = new java.util.HashMap<>();
        if (hits != null && hits > 1) {
            extras.put("hits", hits);
        }
        if (armorHalf) {
            extras.put("armorHalf", 1);
        }
        return extras;
    }
}
