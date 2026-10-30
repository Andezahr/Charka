package app.charka.system.cp2020;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.CharacterRole;
import app.charka.skill.SkillDefinition;
import app.charka.skill.SkillProgression;
import app.charka.skill.StatValue;
import app.charka.system.Currency;
import app.charka.system.GameSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cyberpunk 2020 (Friday Night Firefight).
 */
@Component
@RequiredArgsConstructor
public class Cp2020System implements GameSystem {

    public static final String CODE = "CP2020";

    private static final Currency CURRENCY = new Currency("Евродоллары", "eb");

    private static final CheckResolver CHECK_RESOLVER = new Cp2020CheckResolver();

    private static final SkillProgression SKILL_PROGRESSION = new Cp2020SkillProgression();

    /** Стратегия без состояния; нужна только чтобы посчитать текущие статы */
    private static final Cp2020StatsStrategy STATS = new Cp2020StatsStrategy();

    private final HealthCounterService healthCounterService;

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "Cyberpunk 2020";
    }

    @Override
    public Currency getCurrency() {
        return CURRENCY;
    }

    @Override
    public List<String> getDefaultCounterTypes() {
        return List.of(Cp2020StatsStrategy.TYPE, Cp2020ArmorStrategy.TYPE, Cp2020HealthStrategy.TYPE, Cp2020IpStrategy.TYPE);
    }

    @Override
    public List<String> getQuickDice() {
        return List.of("d10", "d6", "2d6", "3d6");
    }

    @Override
    public CheckResolver getCheckResolver() {
        return CHECK_RESOLVER;
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
        return SKILL_PROGRESSION;
    }

    /**
     * Статы из счётчика характеристик, с текущими штрафами от ран и потери человечности.
     */
    @Override
    public Map<String, StatValue> getCurrentStats(Character character) {
        Map<String, StatValue> stats = new LinkedHashMap<>();
        CounterContext context = new CounterContext(null, character.getCounters());
        context.sibling(Cp2020StatsStrategy.TYPE).ifPresent(counter ->
                STATS.describe(new CounterContext(counter, character.getCounters())).getStats().forEach(line ->
                        stats.put(line.getKey(),
                                new StatValue(line.getKey(), line.getName(), line.getBase(), line.getEffective()))));
        return stats;
    }

    @Override
    public List<ItemTypeDefinition> getItemTypes() {
        return Cp2020ItemTypes.ALL;
    }

    @Override
    public List<CheckRequest.Part> getSkillBonuses(Character character, SkillDefinition definition, CharacterSkill skill) {
        return Cp2020SkillRules.bonuses(character, definition, skill);
    }

    /**
     * Естественное лечение: 1 очко в день (правила — в {@link Cp2020HealthStrategy#healOverTime}).
     */
    @Override
    public void onTimePassed(Campaign campaign, int days) {
        healthCounterService.healOverTime(campaign.getId(), days);
    }
}
