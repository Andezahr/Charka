package app.charka.effect;

import app.charka.exception.NotFoundException;
import app.charka.model.Character;
import app.charka.model.TimedEffect;
import app.charka.dice.DiceRoller;
import app.charka.encounter.RoundActions;
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
    private final RoundActions roundActions;

    /**
     * Готовый эффект системы; срок бросается.
     *
     * @return итог: «Boost: +1 INT на 4 ч»
     * @throws IllegalArgumentException если такого эффекта у системы нет
     */
    @Transactional
    public String applyTemplate(Long characterId, String code) {
        Character character = character(characterId);
        EffectTemplate template = systemRegistry.forCharacter(character).time().findEffectTemplate(code)
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
        if (stat != null && !stat.isBlank() && !TimedEffects.ALL_STATS.equals(stat) && !systemRegistry.forCharacter(character).skills().getCurrentStats(character).containsKey(stat)) {
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
        character.getEffects().remove(find(character, effectId));
    }

    /**
     * Потратить эффект в бою (энергетик «Стального Джо»): на своём ходу — ещё действия в этом раунде, эффект снимается.
     *
     * @return итог: «Энергетик: в этом ходу ещё одно действие»
     * @throws IllegalArgumentException эффект ничего не даёт, персонаж не в бою или ход не его
     * @throws NotFoundException если эффекта нет или он чужой
     */
    @Transactional
    public String spend(Long characterId, Long effectId) {
        Character character = character(characterId);
        TimedEffect effect = find(character, effectId);
        if (effect.getExtraActions() <= 0) {
            throw new IllegalArgumentException("Этот эффект не даёт действий");
        }
        if (!roundActions.inBattle(character)) {
            throw new IllegalArgumentException("Тратится в бою: на своём ходу — ещё одно действие");
        }
        roundActions.requireTurnOf(character);
        roundActions.grant(character, effect.getExtraActions());
        character.getEffects().remove(effect);
        return effect.getName() + ": в этом ходу ещё " + (effect.getExtraActions() == 1 ? "одно действие" : effect.getExtraActions() + " действия");
    }

    private static TimedEffect find(Character character, Long effectId) {
        return character.getEffects().stream()
                .filter(found -> found.getId().equals(effectId))
                .findFirst()
                .orElseThrow(NotFoundException.forId("TimedEffect", effectId));
    }

    private Character character(Long characterId) {
        return characterRepository.findById(characterId).orElseThrow(NotFoundException.forId("Character", characterId));
    }

    private static String describe(TimedEffect effect) {
        String change = effect.getStat() == null ? "" : " " + (effect.getModifier() >= 0 ? "+" : "") + effect.getModifier() + " " + effect.getStat().toUpperCase();
        return effect.getName() + ":" + change + " на " + effect.getRemainingLabel();
    }
}
