package app.charka.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

/**
 * Навык персонажа: код из каталога системы ({@code GameSystem.getSkillCatalog()}) и уровень.
 * Обычный навык — одна строка на код (уровень 0 не хранится); у навыков со своим названием
 * («Эксперт: …», языки) строк на один код может быть несколько, они различаются {@code customName}.
 */
@Data
@Entity
public class CharacterSkill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JsonBackReference
    private Character character;

    @Column(name = "skill_code")
    private String skillCode;

    @Column(name = "skill_level")
    private int level;

    @Column(name = "custom_name")
    private String customName;
}
