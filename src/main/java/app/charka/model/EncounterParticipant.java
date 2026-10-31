package app.charka.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Участник боя: его место в очереди раунда.
 */
@Getter
@Setter
@Entity
@Table(name = "encounter_participant")
public class EncounterParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private Character character;

    private int initiative;

    /** Как получена инициатива: показывается подсказкой */
    @Column(name = "initiative_details")
    private String initiativeDetails;

    @Column(name = "turn_order")
    private int turnOrder;

    /** Уже походил в этом раунде */
    private boolean done;

    /** Решил подождать и вступить позже в этом раунде */
    private boolean waiting;

    /** Что объявлено в этом раунде: код из {@code GameSystem.getInitiativeDeclarations}; новый раунд сбрасывает */
    private String declaration;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CombatEffect> effects = new ArrayList<>();
}
