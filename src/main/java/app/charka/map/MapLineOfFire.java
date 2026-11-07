package app.charka.map;

import app.charka.attack.LineOfFire;
import app.charka.model.Character;
import app.charka.model.Encounter;
import app.charka.model.EncounterParticipant;
import app.charka.repository.EncounterRepository;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * M-17: линия огня по карте идущего боя — те же клетки, что в подсказке «На линии огня» меню фишки.
 * Отдельно от {@link BattleFieldService}: тот зависит от боя, а бой — от атаки.
 */
@Component
@RequiredArgsConstructor
public class MapLineOfFire implements LineOfFire {

    private final EncounterRepository encounterRepository;
    private final GameSystemRegistry systemRegistry;

    @Override
    @Transactional(readOnly = true)
    public List<Bystander> between(Character attacker, Character target) {
        if (attacker == null || target == null || attacker.getCampaign() == null) {
            return List.of();
        }
        Optional<Encounter> found = encounterRepository.findByCampaignId(attacker.getCampaign().getId())
                .filter(encounter -> encounter.getMapDoc() != null);
        if (found.isEmpty()) {
            return List.of();
        }
        Encounter encounter = found.get();
        Optional<EncounterParticipant> from = placed(encounter, attacker);
        Optional<EncounterParticipant> to = placed(encounter, target);
        if (from.isEmpty() || to.isEmpty()) {
            return List.of();
        }
        MapRules rules = systemRegistry.get(encounter.getCampaign().getSystem()).getMapRules();
        if (rules == null) {
            return List.of();
        }
        MapGrid grid = new MapGrid(MapDoc.parse(encounter.getMapDoc()), rules);
        int fx = from.get().getPosX();
        int fy = from.get().getPosY();
        List<int[]> path = grid.sight(fx, fy, to.get().getPosX(), to.get().getPosY(), BattleFieldService.COVER_NEAR_CELLS).path();
        int cell = grid.doc().cell();
        return encounter.getParticipants().stream()
                .filter(p -> p != from.get() && p != to.get() && p.getPosX() != null)
                .filter(p -> path.stream().anyMatch(c -> c[0] == p.getPosX() && c[1] == p.getPosY()))
                .map(p -> new Bystander(p.getCharacter(), rules.distance(cell, fx, fy, p.getPosX(), p.getPosY())))
                .sorted(Comparator.comparingInt(Bystander::meters))
                .toList();
    }

    private static Optional<EncounterParticipant> placed(Encounter encounter, Character character) {
        return encounter.getParticipants().stream()
                .filter(p -> p.getCharacter().getId().equals(character.getId()) && p.getPosX() != null)
                .findFirst();
    }
}
