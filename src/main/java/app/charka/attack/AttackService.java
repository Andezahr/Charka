package app.charka.attack;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResult;
import app.charka.check.CheckService;
import app.charka.dice.DiceService;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CombatEffect;
import app.charka.model.EncounterParticipant;
import app.charka.model.Item;
import app.charka.model.PendingBlast;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.repository.CharacterRepository;
import app.charka.repository.EncounterRepository;
import app.charka.repository.ItemRepository;
import app.charka.repository.PendingBlastRepository;
import app.charka.service.counter.HealthCounterService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final EncounterRepository encounterRepository;
    private final GameSystemRegistry systemRegistry;
    private final CheckService checkService;
    private final DiceService diceService;
    private final app.charka.journal.JournalService journalService;
    private final DiceRoller diceRoller;
    private final HealthCounterService healthCounterService;
    private final app.charka.encounter.RoundActions roundActions;
    private final LineOfFire lineOfFire;
    private final MapBlast mapBlast;

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
        // предел действий системы (Космозэки — одно за ход) проверяется до броска и траты удачи
        roundActions.require(attacker);
        List<Character> targets = targets(attacker, request.targetIds());
        Item targetItem = request.targetItemId() == null ? null : targetItem(attacker, rules, request.targetItemId());
        AttackContext context = new AttackContext(attacker, source, targets, request, system::getCurrentStats, this::effectsOf, targetItem);
        if (request.luck() > 0) {
            system.spendLuck(attacker, request.luck());
        }
        AttackResult result = journalService.capture(() -> perform(context, rules), done -> draft(attacker, done));
        // A-05: атака в бою — действие раунда
        roundActions.count(attacker);
        // израсходованный предмет ушёл из инвентаря: его строки на листе больше нет
        boolean spentUp = source.item() == null || source.item().getInventory().getContent().stream().noneMatch(item -> item == source.item());
        return spentUp || result.pendingBlast() != null ? result.withoutItemRow() : result;
    }

    /**
     * Атака способностью по её коду: кулаками.
     *
     * @throws IllegalArgumentException если такой способности у системы нет
     */
    @Transactional
    public AttackResult attackWithAbility(Long characterId, String code, AttackRequest request) {
        Character attacker = characterRepository.findById(characterId)
                .orElseThrow(CharacterNotFoundException.forId(characterId));
        AttackRules rules = systemRegistry.forCharacter(attacker).getAttackRules();
        List<AttackSource> abilities = rules == null ? List.of() : rules.abilities(attacker);
        AttackSource source = abilities.stream().filter(ability -> code.equals(ability.ability())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет такой атаки: " + code));
        return attack(characterId, source, request);
    }

    /**
     * J-01: атака в журнале — заголовок «Avenger → Бустер 1», итог, фишка «Попал» или «Мимо», урон и спасброски целей
     * (их фишки собирает журнал), расчёт по шагам.
     */
    private static app.charka.journal.JournalService.Draft draft(Character attacker, AttackResult result) {
        List<app.charka.journal.JournalService.Chip> chips = new ArrayList<>();
        chips.add(new app.charka.journal.JournalService.Chip(result.hit() ? "hit" : "miss", result.hit() ? "Попал" : "Мимо"));
        if (result.pendingBlast() != null) {
            chips.add(new app.charka.journal.JournalService.Chip("info", "Заряд упал мимо — решает мастер"));
        }
        return new app.charka.journal.JournalService.Draft(attacker.getCampaign(), attacker, app.charka.journal.JournalService.Kind.ATTACK,
                result.title(), result.outcome(), result.hit(), result.total(), result.difficulty(), result.steps(), chips);
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
        // бросок самого атакующего: его число катится в итоге
        CheckResult own = null;
        int hitTargets = 0;
        // удача — к первому броску самого атакующего: спасброски целей в зоне огня её не получают
        int luck = context.request().luck();
        if (plan.checks().isEmpty()) {
            last = rules.outcome(context, plan, null, null, diceRoller);
            lastSummary = apply(context, rules, last, steps);
        }
        for (AttackPlan.PlannedCheck planned : plan.checks()) {
            if (planned.intro() != null) {
                steps.add(planned.intro());
            }
            CheckRequest request = planned.request();
            if (luck > 0 && planned.roller() != null && planned.roller().getId().equals(context.attacker().getId())) {
                request = request.withPart(GameSystem.luckPart(luck));
                luck = 0;
            }
            CheckResult check = planned.roller() != null
                    ? checkService.checkForCharacter(planned.roller().getId(), request)
                    : checkService.checkForCampaign(context.attacker().getCampaign().getId(), request);
            if (own == null && planned.roller() != null && planned.roller().getId().equals(context.attacker().getId())) {
                own = check;
            }
            steps.addAll(check.steps());
            last = rules.outcome(context, plan, planned, check, diceRoller);
            last = stray(context, rules, plan, last, check);
            String summary = apply(context, rules, last, steps);
            lastSummary = summary != null ? summary : lastSummary;
            hitTargets += last.hits().isEmpty() ? 0 : 1;
        }

        // M-23: граната упала на цель на карте — взрыв ломает слабое вокруг и подрывает бочки
        if (mapBlast != null && plan.blast() != null && plan.blast().amount() != null && last != null && last.hit()
                && !context.targets().isEmpty()) {
            MapBlast.Result changed = mapBlast.explodeAt(context.targets().get(0), plan.blast().amount(), plan.blast().radius(), diceRoller);
            steps.addAll(changed.steps());
            detonations(context.attacker(), rules, changed, steps);
        }
        Long pending = pendingBlast(context, last);
        if (plan.checks().size() > 1 || (last.headline() == null && last.hits().isEmpty())) {
            return new AttackResult(plan.title(), "Задето целей: " + hitTargets + " из " + plan.checks().size(), hitTargets > 0, steps, pending, false)
                    .withRoll(own == null ? null : own.total(), own == null ? null : own.difficulty());
        }
        int hitCount = last.hits().size();
        // M-17: промах по цели попал в стоящего на линии огня — заголовок и его раны
        boolean stray = !last.hit() && !last.hits().isEmpty() && last.headline() != null && lastSummary != null;
        String outcome = stray ? last.headline() + ": " + lastSummary
                : last.headline() != null ? last.headline()
                : hitCount > 1 ? "Попаданий: " + hitCount + ". " + lastSummary : lastSummary;
        return new AttackResult(plan.title(), outcome, last.hit(), steps, pending, false)
                .withRoll(own == null ? null : own.total(), own == null ? null : own.difficulty());
    }

    /**
     * M-17: одиночная атака по одной цели на карте не попала — промах может попасть в того, кто стоит на линии огня.
     * Решает система ({@link AttackRules#strayHit}); заряд по площади и зона огня идут своим порядком.
     */
    private AttackOutcome stray(AttackContext context, AttackRules rules, AttackPlan plan, AttackOutcome outcome, CheckResult check) {
        if (lineOfFire == null || outcome.hit() || !outcome.hits().isEmpty() || outcome.scattered() != null
                || plan.blast() != null || plan.checks().size() != 1 || context.targets().size() != 1 || context.targetItem() != null) {
            return outcome;
        }
        List<LineOfFire.Bystander> bystanders = lineOfFire.between(context.attacker(), context.targets().get(0));
        if (bystanders.isEmpty()) {
            return outcome;
        }
        LineOfFire.Bystander first = bystanders.get(0);
        return rules.strayHit(context, check, first.character(), first.meters(), diceRoller)
                .map(stray -> {
                    List<String> steps = new ArrayList<>(outcome.steps());
                    steps.addAll(stray.steps());
                    return new AttackOutcome(steps, stray.hits(), null, List.of(),
                            stray.headline() != null ? stray.headline() : outcome.headline(), false);
                })
                .orElse(outcome);
    }

    /**
     * M-23: попали в бочку или баллон на карте — он взрывается, задевает всех в радиусе и рвёт соседнее.
     *
     * @param byCharacterId кто стрелял; null — мастер
     * @throws IllegalArgumentException если в клетке нечему взрываться или боя на карте нет
     */
    @Transactional
    public AttackResult detonate(Long campaignId, int x, int y, Long byCharacterId) {
        return detonate(campaignId, 0, x, y, byCharacterId);
    }

    /**
     * @param level M-22: уровень карты
     */
    @Transactional
    public AttackResult detonate(Long campaignId, int level, int x, int y, Long byCharacterId) {
        Character by = byCharacterId == null ? null : characterRepository.findById(byCharacterId)
                .filter(found -> found.getCampaign() != null && found.getCampaign().getId().equals(campaignId))
                .orElseThrow(CharacterNotFoundException.forId(byCharacterId));
        Campaign campaign = encounterRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Боя на карте нет")).getCampaign();
        AttackRules rules = systemRegistry.get(campaign.getSystem()).getAttackRules();
        if (rules == null || mapBlast == null) {
            throw new IllegalArgumentException("У этой системы нет взрывов");
        }
        return journalService.capture(() -> {
            List<String> steps = new ArrayList<>();
            MapBlast.Result changed = mapBlast.detonate(campaignId, level, x, y, diceRoller);
            steps.addAll(changed.steps());
            int caught = detonations(by, rules, changed, steps);
            String what = changed.detonations().stream().map(MapBlast.Detonation::label).collect(Collectors.joining(", "));
            return new AttackResult("💥 " + what, "Задето: " + caught, caught > 0, steps).withoutItemRow();
        }, done -> new app.charka.journal.JournalService.Draft(campaign, by, app.charka.journal.JournalService.Kind.ATTACK,
                done.title(), done.outcome(), done.hit(), null, null, done.steps(), List.of()));
    }

    /**
     * Урон подорванного: всем в радиусе, тем же порядком, что заряд по площади.
     *
     * @param by кто вызвал взрыв; null — урон пишется в лог от имени задетого
     * @return сколько задето
     */
    private int detonations(Character by, AttackRules rules, MapBlast.Result changed, List<String> steps) {
        int caught = 0;
        for (MapBlast.Detonation detonation : changed.detonations()) {
            steps.addAll(detonation.steps());
            AreaDamage area = new AreaDamage(detonation.label(), detonation.expression(), detonation.amount(), detonation.radius(),
                    Map.of(), List.of());
            for (Character target : detonation.caught()) {
                steps.add(target.getName() + ":");
                AttackHit hit = rules.blastHit(area, diceRoller);
                steps.addAll(hit.steps());
                applyHit(by != null ? by : target, detonation.label(), target, target.getName(), hit, steps);
                caught++;
            }
        }
        return caught;
    }

    /**
     * @return итог последнего попадания; null — попаданий нет
     */
    private String apply(AttackContext context, AttackRules rules, AttackOutcome outcome, List<String> steps) {
        steps.addAll(outcome.steps());
        String summary = null;
        // в зоне взрыва и люди, и машина: подпись у каждого
        boolean several = context.targets().size() + (context.targetItem() == null ? 0 : 1) > 1;
        for (AttackOutcome.TargetedHit targeted : outcome.hits()) {
            if (several && context.item() != null && (targeted.target() != null || targeted.object() != null)) {
                steps.add((targeted.object() != null ? targeted.object().getName() : targeted.target().getName()) + ":");
            }
            steps.addAll(targeted.hit().steps());
            summary = targeted.object() != null
                    ? applyObjectHit(context.attacker(), context.source().name(), rules, targeted.object(), targeted.hit(), steps)
                    : applyHit(context.attacker(), context.source().name(), targeted.target(), context.targetName(), targeted.hit(), steps);
        }
        return summary;
    }

    /** Урон в прочность предмета: машины нет в бою и ран у неё нет */
    private String applyObjectHit(Character attacker, String weapon, AttackRules rules, Item object, AttackHit hit, List<String> steps) {
        if (hit.damage() == null) {
            return "Попадание: " + object.getName() + ", урон определяет мастер";
        }
        diceService.log(attacker.getCampaign(), attacker, hit.expression(), String.join("; ", hit.steps()), hit.damage().getAmount(),
                "Урон: " + weapon + " → " + object.getName());
        String summary = rules.damageObject(object, hit);
        steps.add(summary);
        return summary;
    }

    /**
     * Предметы кампании, которые можно выбрать целью атаки: машины. По владельцу, затем по названию.
     */
    @Transactional(readOnly = true)
    public List<ObjectTarget> objectTargets(Campaign campaign) {
        GameSystem system = campaign == null ? null : systemRegistry.get(campaign.getSystem());
        AttackRules rules = system == null ? null : system.getAttackRules();
        if (rules == null) {
            return List.of();
        }
        return campaign.getCharacters().stream()
                .sorted(Comparator.comparing(Character::getName))
                .flatMap(character -> character.getInventories().stream())
                .flatMap(inventory -> inventory.getContent().stream().sorted(Comparator.comparing(Item::getName)))
                .filter(rules::isObjectTarget)
                .map(item -> {
                    Character owner = item.getInventory().getCharacter();
                    String state = system.getItemSummary(item).main();
                    return new ObjectTarget("item-" + item.getId(), owner.getId(),
                            item.getName() + " · " + owner.getName() + (state == null ? "" : " · " + state));
                })
                .toList();
    }

    /** Заряд упал мимо: ждёт решения мастера на листе, урон уже брошен и остаётся на сервере */
    private Long pendingBlast(AttackContext context, AttackOutcome outcome) {
        AreaDamage blast = outcome.scattered();
        if (blast == null) {
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
        return journalService.capture(() -> applyBlast(attacker, rules, pending, targets), done -> draft(attacker, done));
    }

    private AttackResult applyBlast(Character attacker, AttackRules rules, PendingBlast pending, List<Character> targets) {
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

    /**
     * Новый раунд: очередной бросок урона длящегося эффекта по этому персонажу.
     *
     * @param expression бросок эффекта
     * @param data       данные системы
     * @return итог для показа мастеру
     */
    @Transactional
    public String effectDamage(Character victim, String label, String expression, Map<String, String> data) {
        AttackRules rules = systemRegistry.forCharacter(victim).getAttackRules();
        AttackHit hit = rules.effectHit(expression, data, diceRoller);
        return applyHit(victim, label, victim, victim.getName(), hit, new ArrayList<>());
    }

    private String applyHit(Character attacker, String weapon, Character target, String targetName, AttackHit hit, List<String> steps) {
        String summary = hit.damage() != null ? applyDamage(attacker, weapon, target, targetName, hit, steps)
                : hit.hasEffect() ? "Попадание: " + hit.where()
                : "Попадание: " + hit.where() + ", урон определяет мастер";
        if (hit.save() != null) {
            Optional<HealthEvent> saved = target == null ? Optional.empty()
                    : healthCounterService.save(target.getId(), hit.save().code(), hit.save().modifier());
            if (saved.isPresent()) {
                steps.addAll(saved.get().getSteps());
                summary = saved.get().getSummary();
            } else {
                steps.add("Спасбросок с " + hit.save().modifier() + " бросает мастер: у цели нет здоровья на листе");
            }
        }
        hit.effects().forEach(effect -> applyEffect(effect.onAttacker() ? attacker : target, effect, steps));
        if (!hit.clears().isEmpty()) {
            participantOf(attacker).ifPresent(participant -> participant.getEffects().removeIf(effect -> hit.clears().contains(effect.getLabel())));
        }
        return summary;
    }

    private Set<String> effectsOf(Character character) {
        return participantOf(character)
                .map(participant -> participant.getEffects().stream().map(CombatEffect::getLabel).collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private Optional<EncounterParticipant> participantOf(Character character) {
        if (character == null || character.getCampaign() == null) {
            return Optional.empty();
        }
        return encounterRepository.findByCampaignId(character.getCampaign().getId())
                .flatMap(encounter -> encounter.getParticipants().stream()
                        .filter(other -> other.getCharacter().getId().equals(character.getId()))
                        .findFirst());
    }

    /** Эффект считает раунды у участника боя; вне боя его ведёт мастер */
    private void applyEffect(Character target, LingeringEffect effect, List<String> steps) {
        Optional<EncounterParticipant> participant = participantOf(target);
        if (participant.isEmpty()) {
            steps.add(effect.describe() + " — вне боя ведёт мастер");
            return;
        }
        // HOMEBREW: одинаковые эффекты не складываются, новый заменяет старый: книга молчит
        participant.get().getEffects().removeIf(other -> other.getLabel().equals(effect.label()));
        CombatEffect combatEffect = new CombatEffect();
        combatEffect.setParticipant(participant.get());
        combatEffect.setLabel(effect.label());
        combatEffect.setRoundsLeft(effect.rounds());
        combatEffect.setDamageRolls(effect.damage());
        combatEffect.setAttackOption(effect.attackOption());
        combatEffect.getData().putAll(effect.data());
        participant.get().getEffects().add(combatEffect);
        steps.add(effect.describe());
    }

    private String applyDamage(Character attacker, String weapon, Character target, String targetName, AttackHit hit, List<String> steps) {
        int amount = hit.damage().getAmount();
        diceService.log(attacker.getCampaign(), attacker, hit.expression(), String.join("; ", hit.steps()), amount,
                "Урон: " + weapon + " → " + (target != null ? target.getName() : targetName));
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

    private Item targetItem(Character attacker, AttackRules rules, Long itemId) {
        Item item = itemRepository.findById(itemId).orElseThrow(NotFoundException.forId("Item", itemId));
        Character owner = item.getInventory().getCharacter();
        boolean sameCampaign = attacker.getCampaign() != null && owner.getCampaign() != null
                && attacker.getCampaign().getId().equals(owner.getCampaign().getId());
        if (!sameCampaign) {
            throw new IllegalArgumentException("Цель должна быть из той же кампании");
        }
        if (!rules.isObjectTarget(item)) {
            throw new IllegalArgumentException("Это нельзя выбрать целью: " + item.getName());
        }
        return item;
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
