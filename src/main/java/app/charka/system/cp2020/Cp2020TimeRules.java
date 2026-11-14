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
import app.charka.system.TimeRules;

/**
 * ARC-01: время: что происходит за прошедшие дни, заработок, шаблоны регулярных трат и временных эффектов — правила этой системы (вынесено из Cp2020System).
 */
class Cp2020TimeRules implements TimeRules {

    private final HealthCounterService healthCounterService;

    Cp2020TimeRules(HealthCounterService healthCounterService) {
        this.healthCounterService = healthCounterService;
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

    /** A-10: зарплата по таблице с. 58 — по лучшей роли и её спецспособности */
    @Override
    public app.charka.system.Earning earnings(Character character, int days) {
        return Cp2020Creation.earnings(character, days);
    }

    @Override
    public List<app.charka.system.ExpenseTemplate> getExpenseTemplates() {
        return Cp2020Lifestyle.TEMPLATES;
    }

    @Override
    public List<app.charka.effect.EffectTemplate> getEffectTemplates() {
        return Cp2020Drugs.TEMPLATES;
    }
}
