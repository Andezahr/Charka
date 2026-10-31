package app.charka.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Идущий бой кампании; в кампании он один. Без @Data: toString и hashCode не должны ходить по связям.
 */
@Getter
@Setter
@Entity
@Table(name = "encounter")
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "round_number")
    private int round = 1;

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("turnOrder ASC")
    private List<EncounterParticipant> participants = new ArrayList<>();
}
