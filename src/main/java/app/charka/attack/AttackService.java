package app.charka.attack;

import app.charka.check.CheckResult;
import app.charka.check.CheckService;
import app.charka.dice.DiceService;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.PendingBlast;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.repository.CharacterRepository;
import app.charka.repository.ItemRepository;
import app.charka.repository.PendingBlastRepository;
import app.charka.service.counter.HealthCounterService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Атака: ход один, план и итоги каждой проверки даёт система кампании; урон применяется к цели, всё пишется в лог бросков.
 */
@Service
@RequiredArgsConstructor
public class AttackService {

    private final CharacterRepository characterRepository;
    private final ItemRepository itemRepository;
    private final PendingBlastRepository pendingBlastRepository;
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
        Item weapon = itemRepository.findById(itemId)
                .filter(item -> characterId.equals(item.getInventory().getCharacter().getId()))
                .orElseThrow(NotFoundException.forId("Item", itemId));
        return attack(characterId, AttackSource.of(weapon), request);
    }

    /**
     * Атака чем угодно, что понимает система: предметом или способностью.
     *
     * @throws IllegalArgumentException если атака невозможна
     */
    @Transactional
    public AttackResult attack(Long characterId, AttackSource source, AttackRequest request) {
        Character attacker = characterRepository.findById(characterId)
                .orElseThrow(CharacterNotFoundException.forId(characterId));
        GameSystem system = systemRegistry.forCharacter(attacker);
        AttackRules rules = system.getAttackRules();
        if (rules == null || rules.form(source, system.getCurrentStats(attacker)) == null) {
            throw new IllegalArgumentException("Этим нельзя атаковать: " + source.name());
        }
        List<Character> targets = targets(attacker, request.targetIds());
        AttackContext context = new AttackContext(attacker, source, targets, request, system::getCurrentStats);
        AttackResult result = perform(context, rules);
        // израсходованный предмет ушёл из инвентаря: его строки на листе больше нет
        boolean spentUp = source.item() != null && source.item().getInventory().getContent().stream().noneMatch(item -> item == source.item());
        return spentUp || result.pendingBlast() != null ? result.withoutItemRow() : result;
    }

    private AttackResult perform(AttackContext context, AttackRules rules) {
        AttackPlan plan = rules.plan(context, diceRoller);
        List<String> steps = new ArrayList<>();
        String spent = rules.spend(context);
        if (spent != null) {
            steps.add(spent);
        }

        AttackOutcome last = null;
        String lastSummary = null;
        int hitTargets = 0;
        if (plan.checks().isEmpty()) {
            last = rules.outcome(context, plan, null, null, diceRoller);
            lastSummary = apply(context, last, steps);
        }
        for (AttackPlan.PlannedCheck planned : plan.checks()) {
            if (planned.intro() != null) {
                steps.add(planned.intro());
            }
            CheckResult check = planned.roller() != null
                    ? checkService.checkForCharacter(planned.roller().getId(), planned.request())
                    : checkService.checkForCampaign(context.attacker().getCampaign().getId(), planned.request());
            steps.addAll(check.steps());
            last = rules.outcome(context, plan, planned, check, diceRoller);
            String summary = apply(context, last, steps);
            lastSummary = summary != null ? summary : lastSummary;
            hitTargets += last.hits().isEmpty() ? 0 : 1;
        }

        Long pending = pendingBlast(context, last);
        if (plan.checks().size() > 1 || (last.headline() == null && last.hits().isEmpty())) {
            return new AttackResult(plan.title(), "Задето целей: " + hitTargets + " из " + plan.checks().size(), hitTargets > 0, steps, pending, false);
        }
        int hitCount = last.hits().size();
        String outcome = last.headline() != null ? last.headline()
                : hitCount > 1 ? "Попаданий: " + hitCount + ". " + lastSummary : lastSummary;
        return new AttackResult(plan.title(), outcome, last.hit(), steps, pending, false);
    }

    /**
     * @return итог последнего попадания; null — попаданий нет
     */
    private String apply(AttackContext context, AttackOutcome outcome, List<String> steps) {
        steps.addAll(outcome.steps());
        String summary = null;
        for (AttackOutcome.TargetedHit targeted : outcome.hits()) {
            if (context.targets().size() > 1 && targeted.target() != null && context.item() != null) {
                steps.add(targeted.target().getName() + ":");
            }
            steps.addAll(targeted.hit().steps());
            summary = applyHit(context.attacker(), context.source().name(), targeted.target(), context.targetName(), targeted.hit(), steps);
        }
        return summary;
    }

    /** Заряд упал мимо: ждёт решения мастера на листе, урон уже брошен и остаётся на сервере */
    private Long pendingBlast(AttackContext context, AttackOutcome outcome) {
        AreaDamage blast = outcome.scattered();
        if (blast == null || blast.amount() == null) {
            return null;
        }
        PendingBlast pending = new PendingBlast();
        pending.setCharacter(context.attacker());
        pending.setWeapon(blast.weapon());
        pending.setExpression(blast.expression());
        pending.setAmount(blast.amount());
        pending.setRadius(blast.radius());
        // урон и зона и так в заголовке заряда: здесь только куда он упал
        pending.setNote(outcome.steps().stream().filter(step -> !blast.steps().contains(step)).collect(Collectors.joining("; ")));
        pending.getData().putAll(blast.data());
        outcome.stillCaught().forEach(target -> pending.getStillCaught().add(target.getId()));
        return pendingBlastRepository.save(pending).getId();
    }

    /**
     * Заряды этого персонажа, которые упали мимо и ждут решения.
     */
    @Transactional(readOnly = true)
    public List<PendingBlast> pendingBlasts(Long characterId) {
        return pendingBlastRepository.findByCharacterIdOrderByIdDesc(characterId);
    }

    /**
     * Мастер выбрал, кого задело упавшим зарядом. Заряд применяется один раз.
     *
     * @throws IllegalArgumentException если целей нет
     */
    @Transactional
    public AttackResult applyBlast(Long characterId, Long blastId, List<Long> targetIds) {
        PendingBlast pending = pending(characterId, blastId);
        Character attacker = pending.getCharacter();
        AttackRules rules = systemRegistry.forCharacter(attacker).getAttackRules();
        if (rules == null) {
            throw new IllegalArgumentException("У этой системы нет атак");
        }
        if (targetIds.isEmpty()) {
            throw new IllegalArgumentException("Выберите, кого задело");
        }
        List<Character> targets = targets(attacker, targetIds);
        AreaDamage blast = new AreaDamage(pending.getWeapon(), pending.getExpression(), pending.getAmount(), pending.getRadius(), pending.getData(), List.of());
        List<String> steps = new ArrayList<>();
        for (Character target : targets) {
            steps.add(target.getName() + ":");
            AttackHit hit = rules.blastHit(blast, diceRoller);
            steps.addAll(hit.steps());
            applyHit(attacker, blast.weapon(), target, target.getName(), hit, steps);
        }
        pendingBlastRepository.delete(pending);
        String names = targets.stream().map(Character::getName).collect(Collectors.joining(", "));
        return new AttackResult(blast.weapon() + " → " + names, "Задето целей: " + targets.size(), true, steps).withoutItemRow();
    }

    /**
     * Заряд никого не задел: забыть о нём.
     */
    @Transactional
    public void dismissBlast(Long characterId, Long blastId) {
        pendingBlastRepository.delete(pending(characterId, blastId));
    }

    private PendingBlast pending(Long characterId, Long blastId) {
        return pendingBlastRepository.findById(blastId)
                .filter(blast -> characterId.equals(blast.getCharacter().getId()))
                .orElseThrow(NotFoundException.forId("PendingBlast", blastId));
    }

    private String applyHit(Character attacker, String weapon, Character target, String targetName, AttackHit hit, List<String> steps) {
        if (hit.damage() == null) {
            return "Попадание: " + hit.where() + ", урон определяет мастер";
        }
        int amount = hit.damage().getAmount();
        diceService.log(attacker.getCampaign(), attacker, hit.expression(), String.join("; ", hit.steps()), amount,
                "Урон: " + weapon + " → " + targetName);
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

    private List<Character> targets(Character attacker, List<Long> targetIds) {
        List<Character> targets = new ArrayList<>();
        for (Long id : targetIds) {
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
}
