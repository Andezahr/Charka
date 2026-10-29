package app.charka.dto.counter.response;

import lombok.Data;

import java.util.List;

@Data
public class CounterResponseDto {

    private Long id;
    private String name;
    private String counterType;
    private Integer value;
    private String characterName;
    private Long characterId;
    /** Шаблон с фрагментом card для этого типа счётчика */
    private String viewTemplate = "counters/types/simple";
    /** Название типа для карточки */
    private String typeName;
    /** Данные карточки от стратегии (CounterStrategy.describe); null — карточке хватает value */
    private Object view;
    /** Обязательные счётчики, созданные автоматически вместе с этим */
    private List<String> createdWith = List.of();

    public CounterResponseDto() {}

    public CounterResponseDto(Long id, String name, String counterType, Integer value,
                              String characterName, Long characterId) {
        this.id = id;
        this.name = name;
        this.counterType = counterType;
        this.value = value;
        this.characterName = characterName;
        this.characterId = characterId;
    }
}
