package app.Charka.model;


import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Wound {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Character character;

    private String name;

    private Long severity;
}
