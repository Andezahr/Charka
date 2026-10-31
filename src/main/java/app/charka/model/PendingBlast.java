package app.charka.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Заряд упал мимо цели и ждёт, пока мастер выберет, кого задело. Урон уже брошен и хранится здесь: его нельзя подменить,
 * и он не теряется при перезагрузке страницы. Без @Data: toString и hashCode не должны ходить по связям.
 */
@Getter
@Setter
@Entity
@Table(name = "pending_blast")
public class PendingBlast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private Character character;

    private String weapon;
    private String expression;
    /** Урон; null — у заряда только эффект */
    private Integer amount;
    private int radius;

    /** Как определены отклонение и урон */
    @Column(length = 1000)
    private String note;

    /** Данные системы для попадания */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pending_blast_data", joinColumns = @JoinColumn(name = "blast_id"))
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value")
    private Map<String, String> data = new HashMap<>();

    /** Кто и так остался в зоне: в форме эти цели уже отмечены */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pending_blast_target", joinColumns = @JoinColumn(name = "blast_id"))
    @Column(name = "character_id")
    private Set<Long> stillCaught = new HashSet<>();
}
