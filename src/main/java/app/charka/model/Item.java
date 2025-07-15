package app.charka.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer quantity;
    private Integer cost;
    private String description;

    @ManyToOne
    @JoinColumn(name = "inventory_id")
    @JsonBackReference
    private Inventory inventory;
}
