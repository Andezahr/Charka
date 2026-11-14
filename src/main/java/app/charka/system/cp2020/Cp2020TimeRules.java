package app.charka.system.cp2020;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.service.counter.HealthCounterService;
import java.util.List;
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
