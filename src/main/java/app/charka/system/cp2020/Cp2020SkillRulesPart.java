package app.charka.system.cp2020;

import app.charka.check.CheckRequest;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.counter.CounterContext;
import app.charka.skill.CharacterRole;
import app.charka.skill.SkillDefinition;
import app.charka.skill.SkillProgression;
import app.charka.skill.StatValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import app.charka.skill.SkillRules;

/**
 * ARC-01: навыки, роли, прокачка и текущие статы — правила этой системы (вынесено из Cp2020System).
 */
class Cp2020SkillRulesPart implements SkillRules {

    @Override
    public List<SkillDefinition> getSkillCatalog() {
        return Cp2020Skills.CATALOG;
    }

    @Override
    public List<CharacterRole> getRoles() {
        return Cp2020Skills.ROLES;
    }

    @Override
    public int getRoleUnlockLevel() {
        return Cp2020Rules.ROLE_UNLOCK_LEVEL;
    }

    @Override
    public SkillProgression getSkillProgression() {
        return Cp2020System.SKILL_PROGRESSION;
    }

    /**
     * Статы из счётчика характеристик, с текущими штрафами от ран и потери человечности.
     */
    @Override
    public Map<String, StatValue> getCurrentStats(Character character) {
        Map<String, StatValue> stats = new LinkedHashMap<>();
        CounterContext context = new CounterContext(null, character.getCounters());
        context.sibling(Cp2020StatsStrategy.TYPE).ifPresent(counter ->
                Cp2020System.STATS.describe(new CounterContext(counter, character.getCounters())).getStats().forEach(line ->
                        stats.put(line.getKey(),
                                new StatValue(line.getKey(), line.getName(), line.getBase(), line.getEffective()))));
        return stats;
    }

    @Override
    public List<CheckRequest.Part> getSkillBonuses(Character character, SkillDefinition definition, CharacterSkill skill) {
        return Cp2020SkillRules.bonuses(character, definition, skill);
    }

    @Override
    public java.util.Optional<app.charka.skill.SkillOverride> getSkillOverride(Character character, String skillCode) {
        return Cp2020Chips.override(character, skillCode);
    }
}
