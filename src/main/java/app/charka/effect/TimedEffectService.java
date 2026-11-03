package app.charka.effect;

import app.charka.exception.NotFoundException;
import app.charka.model.Character;
import app.charka.model.TimedEffect;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.CharacterRepository;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Временные эффекты на листе: готовые от системы или свои, снять раньше срока.
 */
@Service
@RequiredArgsConstructor
public class TimedEffectService {

    private final CharacterRepository characterRepository;
    private final GameSystemRegistry systemRegistry;
    private final DiceRoller diceRoller;

    /**
     * Готовый эффект системы; срок бросается.
     *
     * @return итог: «Boost: +1 INT на 4 ч»
     * @throws IllegalArgumentException если такого эффекта у системы нет
     */
    @Transactional
    public String applyTemplate(Long characterId, String code) {
        Character character = character(characterId);
        EffectTemplate template = systemRegistry.forCharacter(character).findEffectTemplate(code)
                .orElseThrow(() -> new IllegalArgumentException("Нет такого эффекта: " + code));
        return describe(TimedEffects.add(character, template, diceRoller));
    }

    /**
     * Свой эффект: название, стат и правка, срок.
     */
    @Transactional
    public String create(Long characterId, String name, String stat, int modifier, String duration,
                         EffectTemplate.DurationUnit unit, String note) {
        Character character = character(characterId);
        if (stat != null && !stat.isBlank() && !TimedEffects.ALL_STATS.equals(stat) && !systemRegistry.forCharacter(character).getCurrentStats(character).containsKey(stat)) {
            throw new IllegalArgumentException("Нет такого стата: " + stat);
        }
        return describe(TimedEffects.add(character, name, stat, modifier, duration, unit, note, diceRoller));
    }

    /**
     * @throws NotFoundException если эффекта нет или он чужой
     */
    @Transactional
    public void remove(Long characterId, Long effectId) {
        Character character = character(characterId);
        TimedEffect effect = character.getEffects().stream()
                .filter(found -> found.getId().equals(effectId))
                .findFirst()
                .orElseThrow(NotFoundException.forId("TimedEffect", effectId));
        character.getEffects().remove(effect);
    }

    private Character character(Long characterId) {
        return characterRepository.findById(characterId).orElseThrow(NotFoundException.forId("Character", characterId));
    }

    private static String describe(TimedEffect effect) {
        String change = effect.getStat() == null ? "" : " " + (effect.getModifier() >= 0 ? "+" : "") + effect.getModifier() + " " + effect.getStat().toUpperCase();
        return effect.getName() + ":" + change + " на " + effect.getRemainingLabel();
    }
}
