package app.charka.model;

import app.charka.item.ItemTypeDefinition;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

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

    /** Код типа из {@code GameSystem.getItemTypes()} */
    @Column(name = "item_type", nullable = false)
    private String type = ItemTypeDefinition.GENERIC_CODE;

    /** Значения полей типа ({@code ItemField}) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "item_property", joinColumns = @JoinColumn(name = "item_id"))
    @MapKeyColumn(name = "prop_key")
    @Column(name = "prop_value")
    private Map<String, String> properties = new HashMap<>();

    @ManyToOne
    @JoinColumn(name = "inventory_id")
    @JsonBackReference
    private Inventory inventory;
}
