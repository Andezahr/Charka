package app.charka.encounter;

import app.charka.model.Character;
import app.charka.model.EncounterParticipant;
import app.charka.repository.EncounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Действия участника в раунде боя (A-05): атака, перезарядка, медпомощь, проверка из трекера. Из числа система считает
 * штраф следующему действию ({@code AttackRules.actionOption}); новый раунд обнуляет. Вне боя ничего не считается.
 * Отдельно от {@link EncounterService}: его зовут атака, предметы и лечение, а сам трекер от них зависит.
 */
@Component
@RequiredArgsConstructor
public class RoundActions {

    private final EncounterRepository encounterRepository;

    /** Персонаж сделал действие; не в бою — ничего */
    @Transactional
    public void count(Character character) {
        participant(character).ifPresent(participant -> participant.setActions(participant.getActions() + 1));
    }

    /**
     * Сколько действий уже сделано в этом раунде: счётчик и бег по карте — тоже действие.
     */
    public static int done(EncounterParticipant participant) {
        return participant.getActions() + (participant.getMovedMeters() > 0 ? 1 : 0);
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
