package app.charka.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Контакт персонажа: фиксер, коп, бывшая. Кто это, как к персонажу относится и кто кому должен.
 */
@Getter
@Setter
@Entity
@Table(name = "contact")
public class Contact {

    public enum Relation {
        FRIEND("Друг"), ALLY("Союзник"), NEUTRAL("Нейтрально"), RIVAL("Соперник"), ENEMY("Враг"), FAMILY("Семья"), LOVER("Роман");

        private final String label;

        Relation(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** Кто это: фиксер, коп, медтех */
    private String role;

    @Enumerated(EnumType.STRING)
    private Relation relation = Relation.NEUTRAL;

    /** Долги: «должен мне 500», «я должен услугу» */
    private String owes;

    private String note;

    @ManyToOne(optional = false)
    @JsonBackReference
    private Character character;
}
