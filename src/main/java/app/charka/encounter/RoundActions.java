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
    /** BF-02: игрок вне своего хода */
    public static final String NOT_YOUR_TURN = "Сейчас ход не ваш — действовать и двигаться можно на своём ходу";

    private final EncounterRepository encounterRepository;
    /** Через провайдер: системы зависят от сервисов, которые зовут этот класс */
    private final ObjectProvider<GameSystemRegistry> systemRegistry;
    /** BF-02: кто ходит и мастер ли это — через провайдеры: трекер сам зовёт этот класс */
    private final ObjectProvider<EncounterService> encounterService;
    private final ObjectProvider<app.charka.security.AccessService> accessService;

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
        participant(character).ifPresent(participant -> {
            requireTurn(participant);
            require(participant, system(character));
        });
    }

    /**
     * BF-02: игрок действует и двигает фишку только на своём ходу; мастер — когда угодно (правка, NPC вне очереди).
     *
     * @throws IllegalArgumentException сейчас ходит другой
     */
    @Transactional(readOnly = true)
    public void requireTurn(EncounterParticipant participant) {
        app.charka.model.Campaign campaign = participant.getEncounter().getCampaign();
        app.charka.security.AccessService access = accessService.getObject();
        // без вошедшего пользователя — вызов самого сервера (адреса без входа закрыты): проверять некого
        if (!access.isEnabled() || access.current().isEmpty() || access.isGm(campaign)) {
            return;
        }
        Long current = encounterService.getObject().currentCharacterId(campaign.getId());
        if (current == null || !current.equals(participant.getCharacter().getId())) {
            throw new IllegalArgumentException(NOT_YOUR_TURN);
        }
    }

    /** То же по участнику боя кампании: ход фишкой, рывок, проверка из трекера */
    @Transactional(readOnly = true)
    public void requireTurn(Long campaignId, Long participantId) {
        encounterRepository.findByCampaignId(campaignId)
                .flatMap(encounter -> encounter.getParticipants().stream()
                        .filter(participant -> participant.getId().equals(participantId)).findFirst())
                .ifPresent(this::requireTurn);
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
