package app.charka.model.counters;

import app.charka.model.Character;
import jakarta.persistence.*;
import lombok.Data;


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
    private Character character;

    private String type;

    // private List<CounterGroup>

}
