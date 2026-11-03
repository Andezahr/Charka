package app.charka.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Что длится на участнике боя несколько раундов: горение, слепота. Закончился бой — строка удаляется вместе с участником.
 */
@Getter
@Setter
@Entity
@Table(name = "combat_effect")
public class CombatEffect {

    private static final String SEPARATOR = ";";
    /** Столько раундов значит «пока не снимут»: удержание */
    public static final int UNTIL_REMOVED = 99;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "participant_id")
    private EncounterParticipant participant;

    private String label;

    @Column(name = "rounds_left")
    private int roundsLeft;

    /** Броски урона на следующие раунды через «;»; null — урона нет */
    private String damage;

    /** Какой модификатор атаки предложить участнику; null — никакого */
    @Column(name = "attack_option")
    private String attackOption;

    /** Данные системы для урона */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "combat_effect_data", joinColumns = @JoinColumn(name = "effect_id"))
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value")
    private Map<String, String> data = new HashMap<>();

    public void setDamageRolls(List<String> rolls) {
        damage = rolls.isEmpty() ? null : String.join(SEPARATOR, rolls);
    }

    public List<String> getDamageRolls() {
        return damage == null ? List.of() : Arrays.asList(damage.split(SEPARATOR));
    }

    /**
     * Прошёл раунд: один раунд и один бросок урона использованы.
     *
     * @return бросок урона этого раунда; null — урона нет
     */
    public String nextRound() {
        if (roundsLeft < UNTIL_REMOVED) {
            roundsLeft--;
        }
        List<String> rolls = new ArrayList<>(getDamageRolls());
        if (rolls.isEmpty()) {
            return null;
        }
        String roll = rolls.remove(0);
        setDamageRolls(rolls);
        return roll;
    }

    public boolean isOver() {
        return roundsLeft <= 0;
    }

    /** Подпись у участника: сколько осталось и чем грозит */
    public String getSummary() {
        List<String> rolls = getDamageRolls();
        return label + (rolls.isEmpty() ? "" : " " + rolls.get(0)) + (roundsLeft >= UNTIL_REMOVED ? "" : " · раундов " + roundsLeft);
    }
}
