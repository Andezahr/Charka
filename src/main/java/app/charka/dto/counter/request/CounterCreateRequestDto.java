package app.charka.dto.counter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;



@Data
public class CounterCreateRequestDto {

    @NotBlank(message = "Имя счётчика не может быть пустым")
    private String name;

    @NotBlank(message = "Тип счётчика обязателен")
    private String counterType;

    @NotNull(message = "Начальное значение обязательно")
    private Integer initialValue;

    private Long characterId;
}
