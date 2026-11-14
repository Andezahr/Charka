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

    static final SkillProgression SKILL_PROGRESSION = new Cp2020SkillProgression();

    private static final AttackRules ATTACK_RULES = new Cp2020AttackRules();

    /** Стратегия без состояния; нужна только чтобы посчитать текущие статы */
    static final Cp2020StatsStrategy STATS = new Cp2020StatsStrategy();

    static final Cp2020ArmorStrategy ARMOR = new Cp2020ArmorStrategy();

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

    /** KF-07: в книге — Contacts: фиксеры, копы, бывшие */
    @Override
    public String getContactsTitle() {
        return "Контакты";
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
    public AttackRules getAttackRules() {
        return ATTACK_RULES;
    }

    private static final app.charka.map.MapRules MAP_RULES = new Cp2020MapRules();

    @Override
    public app.charka.map.MapRules getMapRules() {
        return MAP_RULES;
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
    public app.charka.system.PrintSheet getPrintSheet(Character character) {
        return Cp2020Print.sheet(character, skills().getCurrentStats(character), ARMOR);
    }

    @Override
    public List<app.charka.system.Reference> getReference() {
        return Cp2020Reference.sections();
    }

    /** A-10: зарплата по таблице с. 58 — по лучшей роли и её спецспособности */
    @Override
    public app.charka.system.Earning earnings(Character character, int days) {
        return Cp2020Creation.earnings(character, days);
    }

    /** A-07: оглушение и смерть — по запросу мастера */
    @Override
    public List<app.charka.item.ItemField.Option> getRequestableSaves() {
        return List.of(new app.charka.item.ItemField.Option(Cp2020HealthStrategy.SAVE_STUN, "Спасбросок от оглушения"),
                new app.charka.item.ItemField.Option(Cp2020HealthStrategy.SAVE_DEATH, "Спасбросок от смерти"));
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

    /**
     * Сетевой бой: статы цели и урон программы. HOMEBREW: урон из Сети идёт в торс без брони (с. 140: «physical damage»), BTM вычитается.
     */
    Cp2020Netrunning.Net net() {
        return new Cp2020Netrunning.Net() {
            @Override
            public Map<String, app.charka.skill.StatValue> stats(Character character) {
                return Cp2020System.this.skills().getCurrentStats(character);
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
    public List<app.charka.effect.EffectTemplate> getEffectTemplates() {
        return Cp2020Drugs.TEMPLATES;
    }

    @Override
    public List<app.charka.system.ExpenseTemplate> getExpenseTemplates() {
        return Cp2020Lifestyle.TEMPLATES;
    }

    /** ARC-01: предметы: типы, справочник, действия, строка на листе, вживлённое */
    private volatile Cp2020ItemRules itemsRules;

    @Override
    public app.charka.item.ItemRules items() {
        if (itemsRules == null) {
            itemsRules = new Cp2020ItemRules(this, healthCounterService, dice);
        }
        return itemsRules;
    }

    /** ARC-01: бой: инициатива, карточка участника, противостояния, действия за раунд */
    private volatile Cp2020CombatRules combatRules;

    @Override
    public app.charka.encounter.CombatRules combat() {
        if (combatRules == null) {
            combatRules = new Cp2020CombatRules(this, healthCounterService);
        }
        return combatRules;
    }

    /** ARC-01: навыки, роли, прокачка и текущие статы */
    private volatile Cp2020SkillRulesPart skillsRules;

    @Override
    public app.charka.skill.SkillRules skills() {
        if (skillsRules == null) {
            skillsRules = new Cp2020SkillRulesPart(this);
        }
        return skillsRules;
    }

    /** ARC-01: удача: сколько осталось, потратить на бросок, вернуть к новой сессии */
    private static final Cp2020LuckRules LUCK = new Cp2020LuckRules();

    @Override
    public app.charka.check.LuckRules luck() {
        return LUCK;
    }
}
