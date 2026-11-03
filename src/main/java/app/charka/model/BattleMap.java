package app.charka.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Карта боя кампании: заготовка, которую мастер готовит заранее. Бой играет на её копии ({@link Encounter#getMapDoc}).
 * Содержимое — документ {@code app.charka.map.MapDoc} в JSON.
 */
@Getter
@Setter
@Entity
@Table(name = "battle_map")
public class BattleMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** JSON {@code MapDoc}: размер, стены, клетки, предметы, зоны, подписи */
    @Column(columnDefinition = "TEXT")
    private String doc;

    /** Подложка (M-11): план здания, скан карты; едет с экспортом кампании */
    private byte[] background;

    /** MIME-тип подложки: image/png, image/jpeg, image/webp, image/gif */
    @Column(name = "background_type")
    private String backgroundType;

    @JsonBackReference
    @ManyToOne(optional = false)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;
}
