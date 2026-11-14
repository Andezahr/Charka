package app.charka.system.kosmozeki;

import app.charka.attack.AttackRules;
import app.charka.check.CheckResolver;
import app.charka.encounter.CombatCard;
import app.charka.encounter.Initiative;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.InspirationCounterStrategy;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcTemplate;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.CharacterRole;
import app.charka.system.Currency;
import app.charka.system.GameSystem;
import app.charka.system.ProfileField;
import app.charka.system.Reference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import app.charka.encounter.CombatRules;

/**
 * ARC-01: бой: инициатива, карточка участника, противостояния, действия за раунд — правила этой системы (вынесено из KzSystem).
 */
class KzCombatRules implements CombatRules {



    /** Инициатива — 1к12 раз за бой; отложенный кубик предсказания заменяет бросок */
    @Override
    public Initiative rollInitiative(Character character, DiceRoller dice) {
        List<String> steps = new ArrayList<>();
        int roll = KzAbilities.d12(character, dice, steps, "к12");
        return new Initiative(roll, steps.get(0).replace("к12: ", ""));
    }

    @Override
    public boolean rollsInitiativeEachRound() {
        return false;
    }

    @Override
    public CombatCard getCombatCard(Character character) {
        Optional<Counter> found = KzAbilities.bodyCounter(character);
        if (found.isEmpty()) {
            return CombatRules.super.getCombatCard(character);
        }
        Counter body = found.get();
        KzBody zones = KzBodyStrategy.body(body);
        List<String> lines = new ArrayList<>();
        for (KzBody.Zone zone : zones.zones()) {
            int hp = KzBodyStrategy.hp(body, zone);
            int armor = KzBodyStrategy.armor(body, zone);
            if (hp < zone.maxHp() || armor > 0) {
                lines.add(zone.name() + " " + hp + "/" + zone.maxHp() + (armor > 0 ? " 🛡" + armor : ""));
            }
        }
        boolean out = KzBodyStrategy.out(body);
        // UX-30: в очереди боя — полоса цифр 0…9 цветом ХП зоны: мастер видит, у кого что на нуле, не раскрывая строку
        List<CombatCard.Segment> segments = new ArrayList<>();
        for (int digit = 0; digit < 10; digit++) {
            int d = digit;
            KzBody.Zone zone = zones.zones().stream().filter(candidate -> candidate.digits().contains(d)).findFirst().orElse(null);
            if (zone == null) {
                segments.add(new CombatCard.Segment(String.valueOf(digit), "none", digit + ": мимо зон"));
                continue;
            }
            int hp = KzBodyStrategy.hp(body, zone);
            int percent = zone.maxHp() == 0 ? 0 : hp * 100 / zone.maxHp();
            String css = hp <= 0 ? "down" : percent < 35 ? "low" : percent < 100 ? "hurt" : "ok";
            segments.add(new CombatCard.Segment(String.valueOf(digit), css, digit + ": " + zone.name() + " " + hp + " из " + zone.maxHp()));
        }
        return new CombatCard(body.getId(), KzBodyStrategy.statusLabel(body), out, out, lines, List.of(), false,
                zones.zones().stream().map(zone -> new CombatCard.Option(zone.code(), zone.name())).toList(), List.of(), segments);
    }

    /** Новый бой: способности «раз за бой» восстанавливаются */
    @Override
    public void onEncounterStart(Character character) {
        KzAbilities.restoreForFight(character);
    }

    /** За ход — действие и перемещение: бег действие не тратит */
    @Override
    public boolean movementIsAction() {
        return false;
    }

    /** За ход — одно действие (RULES_KOSMOZEKI.md, раздел 2): вторая атака, перезарядка или лечение — уже в следующем раунде */
    @Override
    public int actionsPerRound() {
        return 1;
    }
}
