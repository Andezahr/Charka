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
import java.util.Map;
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
        List<Character> targets = targets(attacker, request);
        if (rules.isZoneFire(request)) {
            return zoneFire(attacker, weapon, system, rules, targets, request);
        }
        if (targets.size() > 1) {
            throw new IllegalArgumentException("Такой режим огня — только по одной цели");
        }
        Character target = targets.isEmpty() ? null : targets.get(0);
        String targetName = target != null ? target.getName() : targetName(request);
        String title = weapon.getName() + " → " + targetName;

        CheckRequest toHit = rules.toHit(attacker, weapon, request, system.getCurrentStats(attacker));
        List<String> steps = new ArrayList<>();
        steps.add(rules.spend(weapon, request));
        CheckResult check = checkService.checkForCharacter(attacker.getId(), toHit);
        steps.addAll(check.steps());
        if (check.fumble()) {
            steps.addAll(rules.onFumble(weapon, diceRoller));
        }
        if (!Boolean.TRUE.equals(check.success())) {
            return new AttackResult(title, check.outcome(), false, steps);
        }
        List<AttackHit> hits = rules.hits(weapon, request, check, diceRoller);
        String outcome = applyHits(attacker, weapon, target, targetName, hits, steps);
        return new AttackResult(title, outcome == null ? check.outcome() + ", но ни одна пуля не дошла" : outcome, !hits.isEmpty(), steps);
    }

    /**
     * Огонь по зоне: патроны расходуются один раз, каждая цель бросает сама.
     */
    private AttackResult zoneFire(Character attacker, Item weapon, GameSystem system, AttackRules rules,
                                  List<Character> targets, AttackRequest request) {
        List<CheckRequest> saves = new ArrayList<>();
        for (Character target : targets) {
            saves.add(rules.zoneSave(target, weapon, request, system.getCurrentStats(target)));
        }
        if (targets.isEmpty()) {
            saves.add(rules.zoneSave(null, weapon, request, Map.of()));
        }
        List<String> steps = new ArrayList<>();
        steps.add(rules.spend(weapon, request));
        int hitTargets = 0;
        for (int i = 0; i < saves.size(); i++) {
            Character target = targets.isEmpty() ? null : targets.get(i);
            String name = target != null ? target.getName() : targetName(request);
            CheckResult save = target != null
                    ? checkService.checkForCharacter(target.getId(), saves.get(i))
                    : checkService.checkForCampaign(attacker.getCampaign().getId(), saves.get(i));
            steps.add(name + ": спасбросок");
            steps.addAll(save.steps());
            if (!Boolean.TRUE.equals(save.success())) {
                hitTargets++;
                applyHits(attacker, weapon, target, name, rules.zoneHits(weapon, request, diceRoller), steps);
            }
        }
        return new AttackResult(weapon.getName() + " → зона огня", "Попали под огонь: " + hitTargets + " из " + saves.size(), hitTargets > 0, steps);
    }

    /**
     * @return итог для заголовка; null — попаданий нет
     */
    private String applyHits(Character attacker, Item weapon, Character target, String targetName, List<AttackHit> hits, List<String> steps) {
        String outcome = null;
        for (AttackHit hit : hits) {
            steps.addAll(hit.steps());
            outcome = applyHit(attacker, weapon, target, targetName, hit, steps);
        }
        return hits.size() > 1 ? "Попаданий: " + hits.size() + ". " + outcome : outcome;
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

    private List<Character> targets(Character attacker, AttackRequest request) {
        List<Character> targets = new ArrayList<>();
        for (Long id : request.targetIds()) {
            Character target = characterRepository.findById(id).orElseThrow(CharacterNotFoundException.forId(id));
            if (target.getId().equals(attacker.getId())) {
                throw new IllegalArgumentException("Нельзя атаковать самого себя");
            }
            boolean sameCampaign = attacker.getCampaign() != null && target.getCampaign() != null
                    && attacker.getCampaign().getId().equals(target.getCampaign().getId());
            if (!sameCampaign) {
                throw new IllegalArgumentException("Цель должна быть из той же кампании");
            }
            targets.add(target);
        }
        return targets;
    }

    private static String targetName(AttackRequest request) {
        return request.targetName() == null || request.targetName().isBlank() ? DEFAULT_TARGET : request.targetName().strip();
    }
}
