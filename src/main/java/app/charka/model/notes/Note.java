package app.charka.model.notes;

import app.charka.model.Character;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
public class Note {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max=255, message="Заголовок не должен превышать 255 символов")
    private String name;
    @Column(length = 1000)
    private String text;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "note_category_link",
            joinColumns = @JoinColumn(name = "note_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @JsonManagedReference
    private Set<NoteCategory> categories = new HashSet<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToOne
    @JsonBackReference
    private Character character;
}
