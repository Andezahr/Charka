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
import app.charka.encounter.CombatRules;

/**
 * ARC-01: бой: инициатива, карточка участника, противостояния, действия за раунд — правила этой системы (вынесено из Cp2020System).
 */
class Cp2020CombatRules implements CombatRules {

    private final Cp2020System system;
    private final HealthCounterService healthCounterService;

    Cp2020CombatRules(Cp2020System system, HealthCounterService healthCounterService) {
        this.system = system;
        this.healthCounterService = healthCounterService;
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
    public Initiative rollInitiative(Character character, DiceRoller dice) {
        return Cp2020Initiative.roll(character, system.skills().getCurrentStats(character), dice);
    }

    /**
     * С. 97: «Every round, each player gets to do something. The order of the round is based on an initiative roll of 1D10
     * plus the players REF stat». Автор решил (A-13): каждый раунд. Кампания может переключить ({@code Campaign.initiativeEachRound}).
     */
    @Override
    public boolean rollsInitiativeEachRound() {
        return true;
    }

    @Override
    public CombatCard getCombatCard(Character character) {
        CounterContext context = new CounterContext(null, character.getCounters());
        Optional<Counter> health = context.sibling(Cp2020HealthStrategy.TYPE);
        if (health.isEmpty()) {
            return CombatRules.super.getCombatCard(character);
        }
        Cp2020HealthView view = (Cp2020HealthView) healthCounterService.describe(health.get(), character.getCounters());
        List<String> lines = new ArrayList<>();
        if (view.getDamage() > 0) {
            lines.add("Урон " + view.getDamage() + "/" + view.getMaxDamage());
        }
        context.sibling(Cp2020ArmorStrategy.TYPE).ifPresent(armor ->
                lines.add("SP голова " + Cp2020System.ARMOR.sp(armor, Cp2020Location.HEAD) + " · торс " + Cp2020System.ARMOR.sp(armor, Cp2020Location.TORSO)));
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

    @Override
    public List<app.charka.encounter.CombatContest> getCombatContests() {
        return List.of(Cp2020Facedown.CONTEST);
    }

    @Override
    public app.charka.check.CheckRequest getContestRequest(String code, Character character) {
        if (!Cp2020Facedown.CODE.equals(code)) {
            return CombatRules.super.getContestRequest(code, character);
        }
        return Cp2020Facedown.request(character, system.skills().getCurrentStats(character));
    }

    @Override
    public String getContestLoserEffect(String code, Character winner) {
        return Cp2020Facedown.CODE.equals(code) ? Cp2020Facedown.loserEffect(winner) : null;
    }

    @Override
    public List<String> getCombatCheckSkills() {
        return List.of("awareness");
    }
}
