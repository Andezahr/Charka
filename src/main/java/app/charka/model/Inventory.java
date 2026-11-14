package app.charka.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** VER-01: два изменения одной строки разом — второе получит отказ, а не затрёт первое */
    @jakarta.persistence.Version
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.EqualsAndHashCode.Exclude
    private long version;

    private String name;

    /** R-08: носимый — на листе; неносимый (машина, нычка) — только на вкладке «Снаряжение» */
    @Column(nullable = false)
    private boolean carried = true;

    @JsonManagedReference
    @OneToMany(mappedBy = "inventory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Item> content = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "character_id")
    @JsonBackReference
    @lombok.ToString.Exclude
    private Character character;
}
