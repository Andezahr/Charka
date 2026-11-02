package app.charka.controller;

import app.charka.encounter.CombatCard;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.service.campaign.CampaignService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Экран мастера (U-01): все персонажи кампании одной таблицей — состояние, броня, статы, деньги, эффекты.
 * Что показать о здоровье и броне, решает система ({@link GameSystem#getCombatCard}), как в трекере боя.
 */
@Controller
@RequiredArgsConstructor
public class GmScreenController {

    private final CampaignService campaignService;
    private final GameSystemRegistry systemRegistry;

    /**
     * Строка экрана.
     *
     * @param stats   «REF 7» по порядку системы; пусто — у системы статов нет
     * @param money   баланс
     * @param effects действующие эффекты: «Boost 4 ч»
     * @param luck    сколько удачи осталось; null — в системе её нет
     */
    public record Row(Character character, CombatCard card, List<String> stats, long money, List<String> effects, Integer luck) {
    }

    @GetMapping("/campaign/{campaignId}/gm")
    @Transactional(readOnly = true)
    public String screen(@PathVariable Long campaignId, Model model) {
        Campaign campaign = campaignService.getById(campaignId);
        GameSystem system = systemRegistry.get(campaign.getSystem());
        List<Row> rows = campaign.getCharacters().stream()
                .sorted(Comparator.comparing(Character::isNpc).thenComparing(Character::getName, Comparator.nullsLast(String::compareTo)))
                .map(character -> new Row(character, system.getCombatCard(character),
                        system.getCurrentStats(character).values().stream()
                                .map(stat -> stat.name() + " " + stat.value() + (stat.value() != stat.base() ? "*" : ""))
                                .toList(),
                        character.getMoneyList().stream().map(Money::getAmount).filter(Objects::nonNull).mapToLong(Long::longValue).sum(),
                        character.getEffects().stream().map(effect -> effect.getName() + " " + effect.getRemainingLabel()).toList(),
                        system.hasLuck() ? system.getLuck(character) : null))
                .toList();
        model.addAttribute("campaign", campaign);
        model.addAttribute("gameSystem", system);
        model.addAttribute("rows", rows);
        model.addAttribute("currency", system.getCurrency());
        return "gm";
    }
}
