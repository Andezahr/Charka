package app.charka.system.cp2020;

import app.charka.attack.AttackRules;
import app.charka.attack.AttackSource;
import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
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
import java.util.stream.Stream;

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

    private static final AttackRules ATTACK_RULES = new Cp2020AttackRules();

    /** Стратегия без состояния; нужна только чтобы посчитать текущие статы */
    private static final Cp2020StatsStrategy STATS = new Cp2020StatsStrategy();

    private static final Cp2020ArmorStrategy ARMOR = new Cp2020ArmorStrategy();

    private static final List<CatalogItem> ITEM_CATALOG =
            Stream.of(Cp2020Weapons.CATALOG, Cp2020Magazines.CATALOG, Cp2020Ammo.CATALOG, Cp2020Armors.CATALOG).flatMap(List::stream).toList();

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
    public List<CatalogItem> getItemCatalog() {
        return ITEM_CATALOG;
    }

    @Override
    public List<ItemAction> getItemActions(Item item) {
        return Cp2020WornArmor.isArmor(item) ? Cp2020ArmorActions.actions(item) : Cp2020WeaponActions.actions(item);
    }

    @Override
    public List<String> getItemNotes(Item item) {
        return Cp2020WornArmor.isArmor(item) ? Cp2020ArmorActions.notes(item) : Cp2020WeaponActions.notes(item);
    }

    @Override
    public String performItemAction(Item item, String action, Map<String, String> params) {
        return Cp2020WornArmor.isArmor(item) ? Cp2020ArmorActions.perform(item, action) : Cp2020WeaponActions.perform(item, action, params);
    }

    @Override
    public AttackRules getAttackRules() {
        return ATTACK_RULES;
    }

    @Override
    public List<CheckRequest.Part> getSkillBonuses(Character character, SkillDefinition definition, CharacterSkill skill) {
        return Cp2020SkillRules.bonuses(character, definition, skill);
    }

    /** Роль, состояние, SP торса и чем вооружён */
    @Override
    public List<String> getCharacterBrief(Character character) {
        List<String> brief = new ArrayList<>(GameSystem.super.getCharacterBrief(character));
        CounterContext context = new CounterContext(null, character.getCounters());
        context.sibling(Cp2020HealthStrategy.TYPE).ifPresent(health -> brief.add(healthBrief(health)));
        context.sibling(Cp2020ArmorStrategy.TYPE).ifPresent(armor -> brief.add("SP торса " + ARMOR.sp(armor, Cp2020Location.TORSO)));
        character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(item -> ATTACK_RULES.form(AttackSource.of(item), Map.of()) != null)
                .map(Item::getName)
                .forEach(brief::add);
        return brief;
    }

    private static String healthBrief(Counter health) {
        if (health.getFlag(Cp2020HealthStrategy.DEAD)) {
            return "Мёртв";
        }
        int damage = health.getValue() == null ? 0 : health.getValue();
        return damage == 0 ? "Цел" : Cp2020Rules.levelName(Cp2020Rules.woundLevel(damage)) + " · урон " + damage;
    }

    /** HOMEBREW: копия получает снаряжение как новое: целая броня, рабочее оружие, полный магазин. В книге копий нет */
    @Override
    public void prepareItemCopy(Item item) {
        item.getProperties().keySet().removeIf(key -> key.startsWith("wear_") || key.equals(Cp2020ItemTypes.JAMMED));
        if (Cp2020ItemTypes.WEAPON.equals(item.getType()) && item.getProperties().containsKey(Cp2020ItemTypes.LOADED)) {
            String capacity = item.getProperties().getOrDefault(Cp2020ItemTypes.INSERTED, item.getProperties().get(Cp2020ItemTypes.SHOTS));
            if (capacity != null) {
                item.getProperties().put(Cp2020ItemTypes.LOADED, capacity);
            }
        }
    }

    @Override
    public List<NpcTemplate> getNpcTemplates() {
        return Cp2020Npcs.templates();
    }

    @Override
    public NpcSheet rollNpc(String templateCode, boolean advanced, DiceRoller dice) {
        return Cp2020Npcs.roll(templateCode, advanced, dice);
    }

    /**
     * Естественное лечение: 1 очко в день (правила — в {@link Cp2020HealthStrategy#healOverTime}).
     */
    @Override
    public void onTimePassed(Campaign campaign, int days) {
        healthCounterService.healOverTime(campaign.getId(), days);
    }
}
