package app.charka.system.cp2020;

import app.charka.attack.AttackRules;
import app.charka.attack.AttackSource;
import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.encounter.CombatCard;
import app.charka.encounter.InitiativeDeclaration;
import app.charka.item.InstalledBlock;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.encounter.Initiative;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcSheet;
import app.charka.npc.NpcTemplate;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.CharacterRole;
import app.charka.skill.SkillDefinition;
import app.charka.skill.SkillProgression;
import app.charka.skill.StatValue;
import app.charka.system.Currency;
import app.charka.system.GameSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import app.charka.skill.SkillRules;

/**
 * ARC-01: навыки, роли, прокачка и текущие статы — правила этой системы (вынесено из Cp2020System).
 */
class Cp2020SkillRulesPart implements SkillRules {

    private final Cp2020System system;

    Cp2020SkillRulesPart(Cp2020System system) {
        this.system = system;
    }

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
