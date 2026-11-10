package app.charka.controller;

import app.charka.encounter.CombatCard;
import app.charka.model.Character;
import app.charka.model.Money;
import app.charka.system.GameSystem;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Экран мастера (U-01): все персонажи кампании одной таблицей — состояние, броня, статы, деньги, эффекты.
 * R-10: таблица — список персонажей на обзоре кампании, {@code /gm} перенаправляет туда.
 * Что показать о здоровье и броне, решает система ({@link GameSystem#getCombatCard}), как в трекере боя.
 */
@Controller
public class GmScreenController {

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

    /** R-10: экран мастера — теперь список обзора кампании; адрес остаётся для старых ссылок */
    @GetMapping("/campaign/{campaignId}/gm")
    public String screen(@PathVariable Long campaignId) {
        return "redirect:/campaign/" + campaignId;
    }

    /** Строки персонажей по порядку: игроки, затем NPC, по имени */
    static List<Row> rows(List<Character> characters, GameSystem system) {
        return characters.stream()
                .sorted(Comparator.comparing(Character::isNpc).thenComparing(Character::getName, Comparator.nullsLast(String::compareTo)))
                .map(character -> new Row(character, system.getCombatCard(character),
                        system.getCurrentStats(character).values().stream()
                                .map(stat -> stat.name() + " " + stat.value() + (stat.value() != stat.base() ? "*" : ""))
                                .toList(),
                        character.getMoneyList().stream().map(Money::getAmount).filter(Objects::nonNull).mapToLong(Long::longValue).sum(),
                        character.getEffects().stream().map(effect -> effect.getName() + " " + effect.getRemainingLabel()).toList(),
                        system.hasLuck() ? system.getLuck(character) : null))
                .toList();
    }
}
