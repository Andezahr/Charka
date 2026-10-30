package app.charka.attack;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.check.CheckService;
import app.charka.dice.DiceService;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.repository.CharacterRepository;
import app.charka.repository.ItemRepository;
import app.charka.service.counter.HealthCounterService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Атака оружием: проверка на попадание, расход, урон и его применение к цели; всё пишется в лог бросков.
 * Правила даёт система кампании.
 */
@Service
@RequiredArgsConstructor
public class AttackService {

    private static final String DEFAULT_TARGET = "Цель";

    private final CharacterRepository characterRepository;
    private final ItemRepository itemRepository;
    private final GameSystemRegistry systemRegistry;
    private final CheckService checkService;
    private final DiceService diceService;
    private final DiceRoller diceRoller;
    private final HealthCounterService healthCounterService;

    /**
     * @param characterId атакующий; предмет другого персонажа считается ненайденным
     * @throws IllegalArgumentException если атака невозможна
     */
    @Transactional
    public AttackResult attack(Long characterId, Long itemId, AttackRequest request) {
        Character attacker = characterRepository.findById(characterId)
                .orElseThrow(CharacterNotFoundException.forId(characterId));
        Item weapon = itemRepository.findById(itemId)
                .filter(item -> characterId.equals(item.getInventory().getCharacter().getId()))
                .orElseThrow(NotFoundException.forId("Item", itemId));
        GameSystem system = systemRegistry.forCharacter(attacker);
        AttackRules rules = system.getAttackRules();
        if (rules == null || rules.form(weapon) == null) {
            throw new IllegalArgumentException("Этим предметом нельзя атаковать: " + weapon.getName());
        }
        Character target = target(attacker, request);
        String targetName = target != null ? target.getName() : targetName(request);
        String title = weapon.getName() + " → " + targetName;

        CheckRequest toHit = rules.toHit(attacker, weapon, request, system.getCurrentStats(attacker));
        List<String> steps = new ArrayList<>();
        steps.add(rules.spend(weapon));
        CheckResult check = checkService.checkForCharacter(characterId, toHit);
        steps.addAll(check.steps());
        if (!Boolean.TRUE.equals(check.success())) {
            return new AttackResult(title, check.outcome(), false, steps);
        }
        String outcome = check.outcome();
        for (AttackHit hit : rules.hits(weapon, request, diceRoller)) {
            steps.addAll(hit.steps());
            outcome = applyHit(attacker, weapon, target, targetName, hit, steps);
        }
        return new AttackResult(title, outcome, true, steps);
    }

    private String applyHit(Character attacker, Item weapon, Character target, String targetName, AttackHit hit, List<String> steps) {
        if (hit.damage() == null) {
            return "Попадание: " + hit.where() + ", урон определяет мастер";
        }
        int amount = hit.damage().getAmount();
        diceService.log(attacker.getCampaign(), attacker, hit.expression(), String.join("; ", hit.steps()), amount,
                "Урон: " + weapon.getName() + " → " + targetName);
        Optional<HealthEvent> event = target == null
                ? Optional.empty()
                : healthCounterService.applyDamage(target.getId(), hit.damage());
        if (event.isEmpty()) {
            steps.add((target == null ? "У цели нет листа" : "У цели нет счётчика здоровья") + ": урон применяет мастер");
            return "Попадание: " + hit.where() + ", " + amount + " урона";
        }
        steps.addAll(event.get().getSteps());
        return event.get().getSummary();
    }

    private Character target(Character attacker, AttackRequest request) {
        if (request.targetId() == null) {
            return null;
        }
        Character target = characterRepository.findById(request.targetId())
                .orElseThrow(CharacterNotFoundException.forId(request.targetId()));
        if (target.getId().equals(attacker.getId())) {
            throw new IllegalArgumentException("Нельзя атаковать самого себя");
        }
        boolean sameCampaign = attacker.getCampaign() != null && target.getCampaign() != null
                && attacker.getCampaign().getId().equals(target.getCampaign().getId());
        if (!sameCampaign) {
            throw new IllegalArgumentException("Цель должна быть из той же кампании");
        }
        return target;
    }

    private static String targetName(AttackRequest request) {
        return request.targetName() == null || request.targetName().isBlank() ? DEFAULT_TARGET : request.targetName().strip();
    }
}
