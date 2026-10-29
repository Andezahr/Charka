package app.charka.dto.counter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;



@Data
public class CounterCreateRequestDto {

    @NotBlank(message = "Имя счётчика не может быть пустым")
    private String name;

    @NotBlank(message = "Тип счётчика обязателен")
    private String counterType;

    @NotNull(message = "Начальное значение обязательно")
    private Integer initialValue;

    private Long characterId;

    /** Дополнительные поля, которые показывает форма выбранного типа (attributes[body] и т.п.) */
    private Map<String, Integer> attributes = new HashMap<>();
}
