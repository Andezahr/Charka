package app.charka.model.counters;

import app.charka.model.Character;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;


@Entity
@Data
public class Counter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "counter_value")
    private Integer value;

    @ManyToOne
    @JsonBackReference
    @lombok.ToString.Exclude
    private Character character;

    private String type;

    /**
     * Дополнительное состояние, которое нужно стратегии сверх одного числа
     * (например, для здоровья CP2020: BODY, SP брони по зонам, флаги «оглушён»/«мёртв»).
     * Каждая стратегия сама решает, какие ключи хранит.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "counter_attribute", joinColumns = @JoinColumn(name = "counter_id"))
    @MapKeyColumn(name = "attr_key")
    @Column(name = "attr_value")
    private Map<String, Integer> attributes = new HashMap<>();

    /**
     * K-02: текстовое состояние стратегии сверх чисел (Космозэки: набор зон тела). Ключи выбирает стратегия.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "counter_text", joinColumns = @JoinColumn(name = "counter_id"))
    @MapKeyColumn(name = "text_key")
    @Column(name = "text_value", length = 4000)
    private Map<String, String> texts = new HashMap<>();

    public int getAttribute(String key, int defaultValue) {
        return attributes.getOrDefault(key, defaultValue);
    }

    public boolean getFlag(String key) {
        return getAttribute(key, 0) != 0;
    }

    public void setFlag(String key, boolean flag) {
        if (flag) {
            attributes.put(key, 1);
        } else {
            attributes.remove(key);
        }
    }

    // private List<CounterGroup>

}
