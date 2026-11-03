package app.charka.model.notes;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
@Entity
public class NoteCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** Чья категория; null — общая (остались с тех пор, как категории были глобальными) */
    @ManyToOne
    @JoinColumn(name = "character_id")
    @JsonIgnore
    @ToString.Exclude
    private app.charka.model.Character character;

    @ManyToMany(mappedBy = "categories")
    @JsonIgnore
    private Set<Note> notes = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NoteCategory that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @PreRemove
    private void preRemove() {
        for (Note n : new HashSet<>(notes)) {
            n.getCategories().remove(this);
        }
    }
}
