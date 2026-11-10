package app.charka.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * KF-05: предмет на карте идущего боя: строка справочника системы на клетке. Подобрал — она становится предметом в
 * носимом инвентаре персонажа. Без коллекции в {@link Encounter}: удаляется вместе с боем (ON DELETE CASCADE).
 */
@Getter
@Setter
@Entity
@Table(name = "map_loot")
public class MapLoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "pos_x", nullable = false)
    private int posX;

    @Column(name = "pos_y", nullable = false)
    private int posY;

    @Column(name = "pos_level", nullable = false)
    private int posLevel;

    /** Ключ справочника системы ({@code GameSystem.getItemCatalog()}) */
    @Column(name = "catalog_key", nullable = false)
    private String catalogKey;

    @Column(nullable = false)
    private String name;

    /** Сколько строк справочника: у патронов — пачек */
    @Column(nullable = false)
    private int quantity = 1;
}
