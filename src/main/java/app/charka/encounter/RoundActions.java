package app.charka.encounter;

import app.charka.model.Character;
import app.charka.model.EncounterParticipant;
import app.charka.repository.EncounterRepository;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Действия участника в раунде боя (A-05): атака, перезарядка, медпомощь, проверка из трекера. Из числа система считает
 * штраф следующему действию ({@code AttackRules.actionOption}); новый раунд обнуляет. Вне боя ничего не считается.
 * Предел действий за раунд задаёт система ({@link GameSystem#actionsPerRound()}): его проверяют до броска ({@link #require}).
 * Отдельно от {@link EncounterService}: его зовут атака, предметы и лечение, а сам трекер от них зависит.
 */
@Component
@RequiredArgsConstructor
public class RoundActions {

    /** Отказ, когда действия раунда кончились */
    public static final String NO_ACTIONS_LEFT = "Действие в этом раунде уже сделано — осталось перемещение";

    private final EncounterRepository encounterRepository;
    /** Через провайдер: системы зависят от сервисов, которые зовут этот класс */
    private final ObjectProvider<GameSystemRegistry> systemRegistry;

    /** Персонаж сделал действие; не в бою — ничего */
    @Transactional
    public void count(Character character) {
        participant(character).ifPresent(participant -> participant.setActions(participant.getActions() + 1));
    }

    /**
     * Проверка до действия: в бою у персонажа должно остаться действие этого раунда. Не в бою — можно всё.
     *
     * @throws IllegalArgumentException действий в этом раунде больше нет
     */
    @Transactional(readOnly = true)
    public void require(Character character) {
        participant(character).ifPresent(participant -> require(participant, system(character)));
    }

    /**
     * @throws IllegalArgumentException действий в этом раунде больше нет
     */
    public static void require(EncounterParticipant participant, GameSystem system) {
        if (!canAct(participant, system)) {
            throw new IllegalArgumentException(NO_ACTIONS_LEFT);
        }
    }

    /** Осталось ли действие в этом раунде: у системы без предела — всегда */
    public static boolean canAct(EncounterParticipant participant, GameSystem system) {
        int limit = system.actionsPerRound();
        return limit <= 0 || done(participant, system.movementIsAction()) < limit;
    }

    /**
     * Сколько действий уже сделано в этом раунде: счётчик и бег по карте — тоже действие.
     */
    public static int done(EncounterParticipant participant) {
        return done(participant, true);
    }

    /**
     * @param movementIsAction K-07: бег — действие (CP2020); у Космозэков перемещение отдельно от действия
     */
    public static int done(EncounterParticipant participant, boolean movementIsAction) {
        return participant.getActions() + (movementIsAction && participant.getMovedMeters() > 0 ? 1 : 0);
    }

    private GameSystem system(Character character) {
        return systemRegistry.getObject().forCharacter(character);
    }

    private Optional<EncounterParticipant> participant(Character character) {
        if (character == null || character.getCampaign() == null) {
            return Optional.empty();
        }
        return encounterRepository.findByCampaignId(character.getCampaign().getId())
                .flatMap(encounter -> encounter.getParticipants().stream()
                        .filter(participant -> participant.getCharacter().getId().equals(character.getId()))
                        .findFirst());
    }
}
