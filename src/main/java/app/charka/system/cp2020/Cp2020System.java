package app.charka.system.cp2020;

import app.charka.attack.AttackRules;
import app.charka.attack.AttackSource;
import app.charka.check.CheckResolver;
import app.charka.dice.DiceRoller;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.counter.CounterContext;
import app.charka.dice.DiceRoller;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.SkillProgression;
import app.charka.skill.StatValue;
import app.charka.system.Currency;
import app.charka.system.GameSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    static String healthBrief(Counter health) {
        if (health.getFlag(Cp2020HealthStrategy.DEAD)) {
            return "Мёртв";
        }
        int damage = health.getValue() == null ? 0 : health.getValue();
        return damage == 0 ? "Цел" : Cp2020Rules.levelName(Cp2020Rules.woundLevel(damage)) + " · урон " + damage;
    }

    @Override
    public List<app.charka.system.Reference> getReference() {
        return Cp2020Reference.sections();
    }

    /** A-07: оглушение и смерть — по запросу мастера */
    @Override
    public List<app.charka.item.ItemField.Option> getRequestableSaves() {
        return List.of(new app.charka.item.ItemField.Option(Cp2020HealthStrategy.SAVE_STUN, "Спасбросок от оглушения"),
                new app.charka.item.ItemField.Option(Cp2020HealthStrategy.SAVE_DEATH, "Спасбросок от смерти"));
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
                return healthCounterService.applyDamage(character.getId(), app.charka.counter.health.DamageRequest.builder()
                                .amount(amount)
                                .location(Cp2020Location.TORSO.name())
                                .damageType(Cp2020DamageType.BYPASS.name())
                                .build())
                        .map(app.charka.counter.health.HealthEvent::getSummary)
                        .orElse(null);
            }
        };
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
    private static final Cp2020SkillRulesPart SKILLS = new Cp2020SkillRulesPart();

    @Override
    public app.charka.skill.SkillRules skills() {
        return SKILLS;
    }

    /** ARC-01: удача: сколько осталось, потратить на бросок, вернуть к новой сессии */
    private static final Cp2020LuckRules LUCK = new Cp2020LuckRules();

    @Override
    public app.charka.check.LuckRules luck() {
        return LUCK;
    }

    /** ARC-01: лист персонажа: поля профиля, строка здоровья, заголовок контактов, лист на бумаге */
    private volatile Cp2020SheetRules sheetRules;

    @Override
    public app.charka.system.SheetRules sheet() {
        if (sheetRules == null) {
            sheetRules = new Cp2020SheetRules(this);
        }
        return sheetRules;
    }

    /** ARC-01: время: что происходит за прошедшие дни, заработок, шаблоны регулярных трат и временных эффектов */
    private volatile Cp2020TimeRules timeRules;

    @Override
    public app.charka.system.TimeRules time() {
        if (timeRules == null) {
            timeRules = new Cp2020TimeRules(healthCounterService);
        }
        return timeRules;
    }

    /** ARC-01: nPC: готовые шаблоны и бросок листа по шаблону */
    private static final Cp2020NpcRules NPCS = new Cp2020NpcRules();

    @Override
    public app.charka.npc.NpcRules npcs() {
        return NPCS;
    }
}
