package app.Charka.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
public class Chronicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private LocalDate startDate;
    private LocalDate endDate;

    private String name;

    @ManyToOne
    private Campaign campaign;
}
