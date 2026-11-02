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
            Stream.of(Cp2020Weapons.CATALOG, Cp2020Magazines.CATALOG, Cp2020Ammo.CATALOG, Cp2020Armors.CATALOG, Cp2020Cyberware.CATALOG, Cp2020Chips.CATALOG, Cp2020Drugs.CATALOG, Cp2020Vehicles.CATALOG, Cp2020Netrunning.CATALOG).flatMap(List::stream).toList();

    private final HealthCounterService healthCounterService;
    private final DiceRoller dice;

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
        return List.of(Cp2020StatsStrategy.TYPE, Cp2020ArmorStrategy.TYPE, Cp2020HealthStrategy.TYPE, Cp2020IpStrategy.TYPE, Cp2020RepStrategy.TYPE);
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
        if (Cp2020Cyber.isCyber(item)) {
            return Cp2020Cyber.actions(item);
        }
        if (Cp2020Drugs.isDrug(item)) {
            return Cp2020Drugs.actions(item);
        }
        if (Cp2020Vehicles.isVehicle(item)) {
            return Cp2020Vehicles.actions(item);
        }
        if (Cp2020Netrunning.isDeck(item) || Cp2020Netrunning.isProgram(item)) {
            return Cp2020Netrunning.actions(item);
        }
        return Cp2020WornArmor.isArmor(item) ? Cp2020ArmorActions.actions(item) : Cp2020WeaponActions.actions(item);
    }

    @Override
    public ItemSummary getItemSummary(Item item) {
        if (Cp2020Vehicles.isVehicle(item)) {
            return Cp2020Vehicles.summary(item);
        }
        if (Cp2020Drugs.isDrug(item)) {
            return Cp2020Drugs.summary(item);
        }
        if (Cp2020Netrunning.isDeck(item) || Cp2020Netrunning.isProgram(item)) {
            return Cp2020Netrunning.summary(item);
        }
        ItemSummary summary = Cp2020ItemSheet.summary(item);
        return summary != null ? summary : GameSystem.super.getItemSummary(item);
    }

    @Override
    public ItemAttachments getItemAttachments(Item item) {
        return Cp2020ItemSheet.attachments(item);
    }

    @Override
    public List<CatalogItem> getSuggestedCatalog(Character character) {
        return Cp2020ItemSheet.suggested(character);
    }

    @Override
    public List<String> getItemNotes(Item item) {
        if (Cp2020Vehicles.isVehicle(item)) {
            return Cp2020Vehicles.notes(item);
        }
        if (Cp2020Netrunning.isDeck(item) || Cp2020Netrunning.isProgram(item)) {
            return Cp2020Netrunning.notes(item);
        }
        return Cp2020WornArmor.isArmor(item) ? Cp2020ArmorActions.notes(item) : Cp2020WeaponActions.notes(item);
    }

    @Override
    public String performItemAction(Item item, String action, Map<String, String> params) {
        if (Cp2020Cyber.isCyber(item)) {
            return Cp2020Cyber.perform(item, action, params, dice, healthCounterService);
        }
        if (Cp2020Drugs.isDrug(item)) {
            return Cp2020Drugs.perform(item, action, dice);
        }
        if (Cp2020Vehicles.isVehicle(item)) {
            return Cp2020Vehicles.perform(item, action, params, getCurrentStats(item.getInventory().getCharacter()), getCheckResolver(), dice);
        }
        if (Cp2020Netrunning.isDeck(item) || Cp2020Netrunning.isProgram(item)) {
            return Cp2020Netrunning.perform(item, action, params, getCurrentStats(item.getInventory().getCharacter()), net(), getCheckResolver(), dice);
        }
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
        Cp2020Cyber.shortWarning(character).ifPresent(brief::add);
        context.sibling(Cp2020ArmorStrategy.TYPE).ifPresent(armor -> brief.add("SP торса " + ARMOR.sp(armor, Cp2020Location.TORSO)));
        character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(item -> ATTACK_RULES.form(AttackSource.of(item), Map.of()) != null)
                .map(Item::getName)
                .forEach(brief::add);
        return brief;
    }

    @Override
    public String getHealthStatus(Character character) {
        return character.getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType()))
                .findFirst()
                .map(Cp2020System::healthBrief)
                .orElse(null);
    }

    private static String healthBrief(Counter health) {
        if (health.getFlag(Cp2020HealthStrategy.DEAD)) {
            return "Мёртв";
        }
        int damage = health.getValue() == null ? 0 : health.getValue();
        return damage == 0 ? "Цел" : Cp2020Rules.levelName(Cp2020Rules.woundLevel(damage)) + " · урон " + damage;
    }

    @Override
    public Initiative rollInitiative(Character character, DiceRoller dice) {
        return Cp2020Initiative.roll(character, getCurrentStats(character), dice);
    }

    /** «Every round... The order of the round is based on an initiative roll» (книга, с. 97) */
    @Override
    public boolean rollsInitiativeEachRound() {
        return true;
    }

    /** Состояние, броня головы и торса, штрафы и какие спасброски нужны сейчас */
    /** С. 97: +3 к инициативе, −3 к попаданию, без прицелов; у холодного оружия и боевых искусств — то же */
    @Override
    public List<InitiativeDeclaration> getInitiativeDeclarations() {
        return List.of(new InitiativeDeclaration("FAST_DRAW", "Выхватывает", 3, "fast_draw"));
    }

    /** Sandevistan (с. 76): команда, раунд ожидания, затем +3 на 5 раундов */
    @Override
    public List<InitiativeDeclaration> getInitiativeDeclarations(Character character) {
        List<InitiativeDeclaration> declarations = new ArrayList<>(getInitiativeDeclarations());
        if (Cp2020Cyber.hasSpeedware(character)) {
            declarations.add(new InitiativeDeclaration("SPEEDWARE", Cp2020Cyber.SPEEDWARE, 3, null, 5));
        }
        return declarations;
    }

    @Override
    public String performInstalledAction(Character character, String action, Map<String, String> params) {
        return Cp2020Cyber.therapy(character, action, params);
    }

    @Override
    public InstalledBlock getInstalled(Character character) {
        return Cp2020Cyber.block(character);
    }

    @Override
    public List<String> getCombatCheckSkills() {
        return List.of("awareness");
    }

    @Override
    public CombatCard getCombatCard(Character character) {
        CounterContext context = new CounterContext(null, character.getCounters());
        Optional<Counter> health = context.sibling(Cp2020HealthStrategy.TYPE);
        if (health.isEmpty()) {
            return GameSystem.super.getCombatCard(character);
        }
        Cp2020HealthView view = (Cp2020HealthView) healthCounterService.describe(health.get(), character.getCounters());
        List<String> lines = new ArrayList<>();
        if (view.getDamage() > 0) {
            lines.add("Урон " + view.getDamage() + "/" + view.getMaxDamage());
        }
        context.sibling(Cp2020ArmorStrategy.TYPE).ifPresent(armor ->
                lines.add("SP голова " + ARMOR.sp(armor, Cp2020Location.HEAD) + " · торс " + ARMOR.sp(armor, Cp2020Location.TORSO)));
        lines.add("BTM −" + view.getBtm());
        Cp2020Cyber.shortWarning(character).ifPresent(lines::add);
        if (view.getPenalty() != null) {
            lines.add(view.getPenalty());
        }
        List<CombatCard.Save> saves = new ArrayList<>();
        if (view.isDeathSaveRequired()) {
            saves.add(new CombatCard.Save(Cp2020HealthStrategy.SAVE_DEATH, "Спасбросок от смерти, d10 ≤ " + view.getDeathTarget()));
        }
        if (view.isStunned()) {
            saves.add(new CombatCard.Save(Cp2020HealthStrategy.SAVE_STUN, "Оглушён: спасбросок, d10 ≤ " + view.getStunTarget()));
        }
        return new CombatCard(health.get().getId(), view.getStatusLabel(), view.isDead(),
                view.isDead() || !saves.isEmpty(), lines, saves, view.isDeathSaveRequired(),
                view.getLocations().stream().map(option -> new CombatCard.Option(option.getCode(), option.getLabel())).toList(),
                view.getDamageTypes().stream().map(option -> new CombatCard.Option(option.getCode(), option.getLabel())).toList());
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
     * Лечение по книге (с. 116, {@link Cp2020HealthStrategy#healOverTime}) и обкатка чипов; что случилось — в отчёт.
     */
    @Override
    public List<String> onTimePassed(Campaign campaign, int days) {
        List<String> report = healthCounterService.healOverTime(campaign.getId(), days);
        if (campaign.getCharacters() != null) {
            campaign.getCharacters().forEach(character -> Cp2020Chips.practice(character, days));
        }
        return report == null ? List.of() : report;
    }

    /** Удача (с. 26): любую часть LUCK прибавляют к важному броску, объявив до него; к концу сессии возвращается */
    @Override
    public int getLuck(Character character) {
        return statsCounter(character)
                .map(stats -> Math.max(0, stats.getAttribute(Cp2020StatsStrategy.LUCK, Cp2020StatsStrategy.DEFAULT_STAT)
                        - stats.getAttribute(Cp2020StatsStrategy.LUCK_SPENT, 0)))
                .orElse(0);
    }

    @Override
    public void spendLuck(Character character, int points) {
        if (points <= 0) {
            return;
        }
        int left = getLuck(character);
        if (points > left) {
            throw new IllegalArgumentException("Удачи осталось " + left + ": вернётся в новой сессии");
        }
        statsCounter(character).ifPresent(stats -> stats.getAttributes().put(Cp2020StatsStrategy.LUCK_SPENT,
                stats.getAttribute(Cp2020StatsStrategy.LUCK_SPENT, 0) + points));
    }

    @Override
    public boolean hasLuck() {
        return true;
    }

    @Override
    public void restoreLuck(Character character) {
        statsCounter(character).ifPresent(stats -> stats.getAttributes().remove(Cp2020StatsStrategy.LUCK_SPENT));
    }

    private static java.util.Optional<app.charka.model.counters.Counter> statsCounter(Character character) {
        return character.getCounters().stream()
                .filter(counter -> Cp2020StatsStrategy.TYPE.equals(counter.getType()))
                .findFirst();
    }

    /**
     * Сетевой бой: статы цели и урон программы. HOMEBREW: урон из Сети идёт в торс без брони (с. 140: «physical damage»), BTM вычитается.
     */
    private Cp2020Netrunning.Net net() {
        return new Cp2020Netrunning.Net() {
            @Override
            public Map<String, app.charka.skill.StatValue> stats(Character character) {
                return getCurrentStats(character);
            }

            @Override
            public String damage(Character character, int amount) {
                return healthCounterService.applyDamage(character.getId(), app.charka.model.counters.strategy.health.DamageRequest.builder()
                                .amount(amount)
                                .location(Cp2020Location.TORSO.name())
                                .damageType(Cp2020DamageType.BYPASS.name())
                                .build())
                        .map(app.charka.model.counters.strategy.health.HealthEvent::getSummary)
                        .orElse(null);
            }
        };
    }

    @Override
    public java.util.Set<String> getItemReferenceKeys() {
        return java.util.Set.of(Cp2020Netrunning.LOADED_IN, Cp2020ItemTypes.LIMB_HOST);
    }

    @Override
    public List<app.charka.effect.EffectTemplate> getEffectTemplates() {
        return Cp2020Drugs.TEMPLATES;
    }

    @Override
    public List<app.charka.system.ExpenseTemplate> getExpenseTemplates() {
        return Cp2020Lifestyle.TEMPLATES;
    }

    @Override
    public List<app.charka.encounter.CombatContest> getCombatContests() {
        return List.of(Cp2020Facedown.CONTEST);
    }

    @Override
    public app.charka.check.CheckRequest getContestRequest(String code, Character character) {
        if (!Cp2020Facedown.CODE.equals(code)) {
            return GameSystem.super.getContestRequest(code, character);
        }
        return Cp2020Facedown.request(character, getCurrentStats(character));
    }

    @Override
    public String getContestLoserEffect(String code, Character winner) {
        return Cp2020Facedown.CODE.equals(code) ? Cp2020Facedown.loserEffect(winner) : null;
    }

    @Override
    public java.util.Optional<app.charka.skill.SkillOverride> getSkillOverride(Character character, String skillCode) {
        return Cp2020Chips.override(character, skillCode);
    }
}
