package app.charka.dto.counter.response;

import lombok.Data;

@Data
public class CounterResponseDto {

    private Long id;
    private String name;
    private String counterType;
    private Integer value;
    private String characterName;
    private Long characterId;

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
