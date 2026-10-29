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
}
