package app.charka.system.kosmozeki;

import app.charka.model.Campaign;
import app.charka.dice.DiceRoller;
import app.charka.service.counter.HealthCounterService;
import java.util.ArrayList;
import java.util.List;
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
