package app.charka.model;

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
 * NPC сцены (A-06): по шаблону системы — создаётся при начале сцены, {@code count} штук; или существующий персонаж.
 */
@Getter
@Setter
@Entity
@Table(name = "scene_npc")
public class SceneNpc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scene_id")
    private Scene scene;

    /** Шаблон системы; null — пустой NPC или существующий персонаж */
    @Column(name = "template_code")
    private String templateCode;

    /** Существующий персонаж кампании; null — создать по шаблону */
    @ManyToOne
    @JoinColumn(name = "character_id")
    private Character character;

    /** Имя новых; пусто — по шаблону */
    private String name;

    @Column(name = "npc_count")
    private int count = 1;

    private boolean advanced;

    /** В засаде: в бою, но ждёт и вступает, когда решит мастер */
    private boolean ambush;
}
