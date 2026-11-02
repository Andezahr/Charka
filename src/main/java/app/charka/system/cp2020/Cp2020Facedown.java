package app.charka.system.cp2020;

import app.charka.attack.AttackContext;
import app.charka.check.CheckRequest;
import app.charka.encounter.CombatContest;
import app.charka.model.Character;
import app.charka.model.counters.Counter;
import app.charka.skill.StatValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Facedown (с. 55): 1D10 + COOL + REP с обеих сторон, у труса репутация с минусом. Проигравший отступает
 * или атакует победителя с −3, пока его не победит. Равенство — ничего.
 */
final class Cp2020Facedown {

    static final String CODE = "FACEDOWN";
    static final CombatContest CONTEST = new CombatContest(CODE, "Facedown");
    private static final String EFFECT_PREFIX = "Facedown: уступил ";
    static final int PENALTY = -3;

    private Cp2020Facedown() {
    }

    static CheckRequest request(Character character, Map<String, StatValue> stats) {
        StatValue cool = stats.get(Cp2020StatsStrategy.COOL);
        return CheckRequest.ofParts("Facedown", List.of(
                new CheckRequest.Part("COOL", cool == null ? 0 : cool.value()),
                new CheckRequest.Part("Репутация", reputation(character))), null, null);
    }

    static int reputation(Character character) {
        return character.getCounters().stream()
                .filter(counter -> Cp2020RepStrategy.TYPE.equals(counter.getType()))
                .map(Counter::getValue)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(0);
    }

    static String loserEffect(Character winner) {
        return EFFECT_PREFIX + winner.getName();
    }

    /**
     * −3 к атаке по тому, кому уступил. HOMEBREW: эффект привязан к имени победителя, у одноимённых NPC он общий;
     * атака по нескольким целям штрафа не получает.
     */
    static CheckRequest withPenalty(CheckRequest toHit, AttackContext context) {
        return penalty(context).map(part -> {
            List<CheckRequest.Part> parts = new java.util.ArrayList<>(toHit.parts());
            parts.add(part);
            return new CheckRequest(toHit.label(), toHit.modifier(), toHit.difficulty(), parts, toHit.skillCode());
        }).orElse(toHit);
    }

    static Optional<CheckRequest.Part> penalty(AttackContext context) {
        if (context.targets().size() != 1) {
            return Optional.empty();
        }
        Character target = context.targets().get(0);
        return context.effects(context.attacker()).contains(loserEffect(target))
                ? Optional.of(new CheckRequest.Part("Уступил в Facedown", PENALTY))
                : Optional.empty();
    }
}
