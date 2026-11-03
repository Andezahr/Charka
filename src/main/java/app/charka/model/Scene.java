package app.charka.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Заготовка боя (A-06): карта кампании (её зоны старта — где встают игроки и NPC), NPC и засада.
 * «Начать сцену» создаёт NPC и начинает бой; сцена после боя остаётся. Без коллекции в {@link Campaign}, в экспорт не идёт.
 */
@Getter
@Setter
@Entity
@Table(name = "scene")
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    private String name;

    /** Карта кампании; null — бой без карты */
    @Column(name = "battle_map_id")
    private Long battleMapId;

    /** В бой входят все живые персонажи игроков */
    @Column(name = "with_players")
    private boolean withPlayers = true;

    @Column(name = "group_players")
    private boolean groupPlayers;

    @Column(name = "group_npcs")
    private boolean groupNpcs;

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SceneNpc> npcs = new ArrayList<>();
}
