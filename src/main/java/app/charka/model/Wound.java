package app.charka.model;


import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

@Data
@Entity
public class Wound {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JsonBackReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Character character;

    private String name;

    private Long severity;

    private LocalDate date;
}
