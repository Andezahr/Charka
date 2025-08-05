package app.charka.dto.counter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class CounterUpdateRequestDto {

    @NotNull(message = "Значение изменения обязательно")
    private Integer value;
}
