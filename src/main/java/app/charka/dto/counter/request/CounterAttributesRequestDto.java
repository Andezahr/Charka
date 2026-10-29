package app.charka.dto.counter.request;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Изменение attributes счётчика из формы карточки: поля формы вида attributes[body], attributes[sp_HEAD].
 * Пустые поля приходят как null и пропускаются.
 */
@Data
public class CounterAttributesRequestDto {
    private Map<String, Integer> attributes = new HashMap<>();
}
