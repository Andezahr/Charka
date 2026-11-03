package app.charka.model;

import app.charka.model.counters.Counter;
import app.charka.model.notes.Note;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
// коротко: ссылки детей на персонажа и персонажа на кампанию иначе зацикливают toString
@ToString(onlyExplicitlyIncluded = true)
@Entity
public class Character {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @ToString.Include
    private String name;

    private LocalDate birthDate;

    /** Персонаж мастера, а не игрока */
    @ToString.Include
    private boolean npc;

    /** Коды ролей/классов из {@code GameSystem.getRoles()}; ролей может быть несколько */
    @ElementCollection
    @CollectionTable(name = "character_role", joinColumns = @JoinColumn(name = "character_id"))
    @Column(name = "role_code")
    private Set<String> roles = new HashSet<>();

    @ManyToOne
    @JsonBackReference
    private Campaign campaign;

    /** R-07: основное оружие на листе (id предмета); null или не оружие — первое оружие инвентаря. В экспорт не идёт: id предметов там новые */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long mainWeaponId;

    /** Игрок (U-03): видит этого персонажа; null — персонаж мастера или ещё не отдан */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    @ManyToOne
    @JoinColumn(name = "player_user_id")
    private AppUser player;

    @JsonManagedReference
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Wound> wounds = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Inventory> inventories = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Money> moneyList = new ArrayList<>();

    @JsonManagedReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecurringExpense> recurringExpenses = new ArrayList<>();

    @JsonManagedReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimedEffect> effects = new ArrayList<>();

    @JsonManagedReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contact> contacts = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Note> notes = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Counter> counters = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterSkill> skills = new ArrayList<>();
}
