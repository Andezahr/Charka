package app.charka.attack;

import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.skill.StatValue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Одна атака.
 *
 * @param targets цели с листом: от них зависит сложность там, где её задаёт цель
 * @param statsOf текущие статы любого участника
 * @param effectsOf что сейчас длится на участнике боя: названия эффектов; вне боя пусто
 */
public record AttackContext(Character attacker, AttackSource source, List<Character> targets, AttackRequest request,
                            Function<Character, Map<String, StatValue>> statsOf, Function<Character, Set<String>> effectsOf) {

    private static final String DEFAULT_TARGET = "Цель";

    public AttackContext {
        targets = List.copyOf(targets);
    }

    public Item item() {
        return source.item();
    }

    public Set<String> effects(Character character) {
        return effectsOf.apply(character);
    }

    public Map<String, StatValue> stats(Character character) {
        return statsOf.apply(character);
    }

    /** Имя цели без листа */
    public String targetName() {
        String name = request.targetName();
        return name == null || name.isBlank() ? DEFAULT_TARGET : name.strip();
    }
}
