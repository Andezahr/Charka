package app.charka.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Entity
public class Campaign {
    @Id
    @GeneratedValue
    private Long id;

    private String name;

    /** Код игровой системы ({@code GameSystem.getCode()}), задаётся при создании кампании */
    @Column(name = "game_system", nullable = false, length = 32)
    private String system = "GENERIC";

    private LocalDate startDate;
    @Column(name = "current_date_value")
    private LocalDate currentDate;

    @JsonManagedReference
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Chronicle> chronicles;

    @JsonManagedReference
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Character> characters;

}
