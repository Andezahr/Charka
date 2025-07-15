package app.Charka.model;

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
    private Inventory inventory;
}
