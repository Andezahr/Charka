package app.charka.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.util.ArrayList;
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

    /** Мастер (U-03): видит и меняет всё в кампании; в экспорт не идёт — импортировавший сам становится мастером */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne
    @JoinColumn(name = "gm_user_id")
    private AppUser gm;

    /** Приглашение игроков (A-02): {@code /join/{token}}; null — ещё не выдано. В экспорт не идёт */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "invite_token", length = 64)
    private String inviteToken;

    /**
     * A-13: инициатива бросается заново каждый раунд; иначе раз за бой. Новой кампании ставится по системе
     * ({@code GameSystem.rollsInitiativeEachRound}), дальше мастер переключает в бою.
     */
    @Column(name = "initiative_each_round")
    private boolean initiativeEachRound = true;

    private LocalDate startDate;
    @Column(name = "current_date_value")
    private LocalDate currentDate;

    @JsonManagedReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Chronicle> chronicles = new ArrayList<>();

    @JsonManagedReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Character> characters = new ArrayList<>();

    /** Карты боя (M-01): заготовки, бой играет на копии */
    @JsonManagedReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<BattleMap> maps = new ArrayList<>();

}
