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
import app.charka.system.TimeRules;

/**
 * ARC-01: время: что происходит за прошедшие дни, заработок, шаблоны регулярных трат и временных эффектов — правила этой системы (вынесено из KzSystem).
 */
class KzTimeRules implements TimeRules {

    private final HealthCounterService healthCounterService;
    private final DiceRoller dice;

    KzTimeRules(HealthCounterService healthCounterService, DiceRoller dice) {
        this.healthCounterService = healthCounterService;
        this.dice = dice;
    }

    /**
     * Промотка времени: отдых лечит каждую зону (сон +30, сон и еда +60 в день), после сна предсказание бросается заново.
     */
    @Override
    public List<String> onTimePassed(Campaign campaign, int days) {
        List<String> report = new ArrayList<>(Optional.ofNullable(healthCounterService.healOverTime(campaign.getId(), days)).orElse(List.of()));
        if (days > 0 && campaign.getCharacters() != null) {
            campaign.getCharacters().forEach(character -> KzAbilities.counter(character, KzPortentStrategy.TYPE).ifPresent(portent -> {
                KzPortentStrategy.roll(portent, dice);
                report.add(character.getName() + ": предсказание " + KzPortentStrategy.dice(portent).values());
            }));
        }
        return report;
    }
}
