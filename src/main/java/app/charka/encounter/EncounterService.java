package app.charka.encounter;

import app.charka.attack.AttackForm;
import app.charka.attack.AttackRules;
import app.charka.attack.AttackService;
import app.charka.attack.AttackSource;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CombatEffect;
import app.charka.model.Encounter;
import app.charka.model.EncounterParticipant;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import app.charka.repository.EncounterRepository;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.SkillCheck;
import app.charka.skill.SkillService;
import app.charka.skill.SkillSheet;
import app.charka.skill.StatValue;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Боевой трекер: участники, очередь, раунды. Инициативу и состояние участника даёт система кампании.
 * Чей ход, не хранится, а считается: первый в очереди, кто ещё не ходил и не выбыл, поэтому убитый участник пропускается сам.
 */
@Service
@RequiredArgsConstructor
public class EncounterService {

    private static final String CUSTOM_SKILL = "custom:";

    private final EncounterRepository encounterRepository;
    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;
    private final GameSystemRegistry systemRegistry;
    private final DiceRoller diceRoller;
    private final HealthCounterService healthCounterService;
    private final AttackService attackService;
    private final SkillService skillService;

    /**
     * Раунд идущего боя кампании; null — боя нет.
     */
    @Transactional(readOnly = true)
    public Integer activeRound(Long campaignId) {
        return encounterRepository.findByCampaignId(campaignId).map(Encounter::getRound).orElse(null);
    }

    /**
     * Кампания персонажа, если в ней идёт бой; null — боя нет: вкладка «Бой» на страницах персонажа не показывается.
     */
    @Transactional(readOnly = true)
    public Long campaignInFight(Long characterId) {
        return characterRepository.findById(characterId)
                .map(Character::getCampaign)
                .filter(campaign -> encounterRepository.findByCampaignId(campaign.getId()).isPresent())
                .map(Campaign::getId)
                .orElse(null);
    }

    /**
     * Кого можно взять в бой: игроки, затем NPC.
     */
    @Transactional(readOnly = true)
    public List<EncounterView.Candidate> candidates(Long campaignId) {
        Campaign campaign = campaign(campaignId);
        GameSystem system = system(campaign);
        return campaign.getCharacters().stream()
                .sorted(Comparator.comparing(Character::isNpc))
                .map(character -> new EncounterView.Candidate(character, system.getCombatCard(character)))
                .toList();
    }

    /**
     * Начать бой: инициатива бросается сразу всем.
     *
     * @throws IllegalArgumentException если бой уже идёт, участников нет или они из другой кампании
     */
    @Transactional
    public Encounter start(Long campaignId, List<Long> characterIds) {
        return start(campaignId, characterIds, false, false);
    }

    /**
     * @param groupPlayers игроки бросают инициативу одним кубиком
     * @param groupNpcs    NPC бросают инициативу одним кубиком
     */
    @Transactional
    public Encounter start(Long campaignId, List<Long> characterIds, boolean groupPlayers, boolean groupNpcs) {
        Campaign campaign = campaign(campaignId);
        if (encounterRepository.findByCampaignId(campaignId).isPresent()) {
            throw new IllegalArgumentException("Бой уже идёт");
        }
        if (characterIds.isEmpty()) {
            throw new IllegalArgumentException("Выберите, кто участвует в бою");
        }
        GameSystem system = system(campaign);
        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setGroupPlayers(groupPlayers);
        encounter.setGroupNpcs(groupNpcs);
        RoundDice dice = new RoundDice(encounter);
        for (Long characterId : Set.copyOf(characterIds)) {
            EncounterParticipant participant = new EncounterParticipant();
            participant.setEncounter(encounter);
            participant.setCharacter(member(campaign, characterId));
            roll(participant, system, dice);
            encounter.getParticipants().add(participant);
        }
        sortByInitiative(encounter);
        return encounterRepository.save(encounter);
    }

    /**
     * Страница боя; пусто — боя нет.
     */
    @Transactional(readOnly = true)
    public Optional<EncounterView> view(Long campaignId) {
        return encounterRepository.findByCampaignId(campaignId).map(this::view);
    }

    private EncounterView view(Encounter encounter) {
        GameSystem system = system(encounter.getCampaign());
        EncounterParticipant current = current(encounter, system);
        List<Character> fighters = ordered(encounter).stream().map(EncounterParticipant::getCharacter).toList();
        List<EncounterView.Row> rows = ordered(encounter).stream().map(participant -> {
            Character character = participant.getCharacter();
            return new EncounterView.Row(participant, character, declaration(participant, system), system.getInitiativeDeclarations(), checks(character, system),
                    system.getCombatCard(character), participant == current,
                    weapons(character, system), targets(character, fighters, system), attackService.pendingBlasts(character.getId()),
                    List.copyOf(participant.getEffects()), abilities(character, system));
        }).toList();
        Set<Long> inFight = fighters.stream().map(Character::getId).collect(Collectors.toSet());
        List<Character> addable = encounter.getCampaign().getCharacters().stream()
                .filter(character -> !inFight.contains(character.getId()))
                .filter(character -> !system.getCombatCard(character).out())
                .sorted(Comparator.comparing(Character::isNpc))
                .toList();
        boolean roundFresh = encounter.getParticipants().stream().noneMatch(EncounterParticipant::isDone);
        return new EncounterView(encounter, current == null ? null : current.getCharacter(),
                nextAfter(encounter, current, system) == null, rows, addable, roundFresh);
    }

    private List<EncounterView.Weapon> weapons(Character character, GameSystem system) {
        AttackRules rules = system.getAttackRules();
        if (rules == null) {
            return List.of();
        }
        Map<String, StatValue> stats = system.getCurrentStats(character);
        List<EncounterView.Weapon> weapons = new ArrayList<>();
        character.getInventories().forEach(inventory -> inventory.getContent().forEach(item -> {
            AttackForm form = rules.form(AttackSource.of(item), stats);
            if (form != null) {
                weapons.add(new EncounterView.Weapon(item, system.getItemSummary(item), form, system.getItemActions(item),
                        "/character/" + character.getId() + "/inventories/" + inventory.getId() + "/items/" + item.getId()));
            }
        }));
        return weapons;
    }

    private static InitiativeDeclaration declaration(EncounterParticipant participant, GameSystem system) {
        return system.getInitiativeDeclarations().stream()
                .filter(declaration -> declaration.code().equals(participant.getDeclaration()))
                .findFirst().orElse(null);
    }

    /** Сначала то, что система считает нужным в бою; остальное — только изученное */
    private List<EncounterView.CheckOption> checks(Character character, GameSystem system) {
        if (!skillService.hasCatalog(character.getId())) {
            return List.of();
        }
        List<String> always = system.getCombatCheckSkills();
        return skillService.getSheet(character.getId()).groups().stream()
                .flatMap(group -> group.rows().stream())
                .filter(row -> row.level() > 0 || always.contains(row.code()))
                .sorted(Comparator.comparing((SkillSheet.Row row) -> !always.contains(row.code())).thenComparing(SkillSheet.Row::name))
                .map(row -> new EncounterView.CheckOption(row.customId() != null ? "custom:" + row.customId() : row.code(),
                        row.name() + " " + row.total()))
                .toList();
    }

    private List<EncounterView.Ability> abilities(Character character, GameSystem system) {
        AttackRules rules = system.getAttackRules();
        if (rules == null) {
            return List.of();
        }
        Map<String, StatValue> stats = system.getCurrentStats(character);
        return rules.abilities(character).stream()
                .map(source -> new EncounterView.Ability(source.ability() + "-" + character.getId(),
                        "/character/" + character.getId() + "/attack/ability/" + source.ability(), source.name(), rules.form(source, stats)))
                .toList();
    }

    /** Сначала другая сторона: это самая вероятная цель, и она будет выбрана сразу */
    private List<Character> targets(Character attacker, List<Character> fighters, GameSystem system) {
        return fighters.stream()
                .filter(other -> !other.getId().equals(attacker.getId()))
                .filter(other -> !system.getCombatCard(other).out())
                .sorted(Comparator.comparing(other -> other.isNpc() == attacker.isNpc()))
                .toList();
    }

    /**
     * Что вышло из «Следующий ход».
     *
     * @param character персонаж, чей теперь ход; null — ходить некому
     * @param report    что случилось в начале нового раунда: горение и прочее
     */
    public record Turn(Long character, List<String> report) {
    }

    /**
     * Текущий участник закончил ход. Если ходить больше некому, начинается новый раунд.
     */
    @Transactional
    public Turn next(Long campaignId) {
        Encounter encounter = encounter(campaignId);
        GameSystem system = system(encounter.getCampaign());
        EncounterParticipant current = current(encounter, system);
        if (current != null) {
            current.setDone(true);
            current.setWaiting(false);
        }
        EncounterParticipant next = current(encounter, system);
        List<String> report = List.of();
        if (next == null) {
            report = effectsOfNewRound(encounter, system);
            startRound(encounter, system);
            next = current(encounter, system);
        }
        return new Turn(next == null ? null : next.getCharacter().getId(), report);
    }

    /**
     * Участник ждёт и вступит позже в этом раунде.
     */
    @Transactional
    public void waitTurn(Long campaignId, Long participantId) {
        EncounterParticipant participant = participant(campaignId, participantId);
        if (!participant.isDone()) {
            participant.setWaiting(true);
        }
    }

    /**
     * Ждавший вступает: ходит прямо сейчас, затем ход возвращается к тому, кого он опередил (книга, с. 97).
     */
    @Transactional
    public void join(Long campaignId, Long participantId) {
        EncounterParticipant participant = participant(campaignId, participantId);
        Encounter encounter = participant.getEncounter();
        EncounterParticipant current = current(encounter, system(encounter.getCampaign()));
        participant.setWaiting(false);
        if (current != null && current != participant) {
            List<EncounterParticipant> order = new ArrayList<>(ordered(encounter));
            order.remove(participant);
            order.add(order.indexOf(current), participant);
            renumber(order);
        }
    }

    /**
     * Ввести персонажа в идущий бой. Инициатива бросается сразу; с высокой он ходит следующим и не отнимает ход у текущего.
     *
     * @throws IllegalArgumentException если он уже в бою или из другой кампании
     */
    @Transactional
    public void add(Long campaignId, Long characterId) {
        Encounter encounter = encounter(campaignId);
        GameSystem system = system(encounter.getCampaign());
        Character character = member(encounter.getCampaign(), characterId);
        if (encounter.getParticipants().stream().anyMatch(other -> other.getCharacter().getId().equals(characterId))) {
            throw new IllegalArgumentException(character.getName() + " уже в бою");
        }
        EncounterParticipant current = current(encounter, system);
        EncounterParticipant added = participant(encounter, character, system);
        List<EncounterParticipant> order = new ArrayList<>(ordered(encounter));
        int place = 0;
        while (place < order.size() && order.get(place).getInitiative() >= added.getInitiative()) {
            place++;
        }
        if (current != null) {
            place = Math.max(place, order.indexOf(current) + 1);
        }
        order.add(place, added);
        renumber(order);
        encounter.getParticipants().add(added);
    }

    /**
     * Убрать участника из боя.
     */
    @Transactional
    public void remove(Long campaignId, Long participantId) {
        EncounterParticipant participant = participant(campaignId, participantId);
        participant.getEncounter().getParticipants().remove(participant);
    }

    /**
     * Участник объявляет в начале раунда то, что меняет его инициативу.
     *
     * @throws IllegalArgumentException если раунд уже идёт или уже объявлено
     */
    @Transactional
    public void declare(Long campaignId, Long participantId, String code) {
        EncounterParticipant participant = participant(campaignId, participantId);
        Encounter encounter = participant.getEncounter();
        InitiativeDeclaration declaration = system(encounter.getCampaign()).getInitiativeDeclarations().stream()
                .filter(known -> known.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Такого объявления нет: " + code));
        if (encounter.getParticipants().stream().anyMatch(EncounterParticipant::isDone)) {
            throw new IllegalArgumentException("Это объявляют в начале раунда, пока никто не походил");
        }
        if (participant.getDeclaration() != null) {
            throw new IllegalArgumentException("Уже объявлено в этом раунде");
        }
        participant.setDeclaration(code);
        participant.setInitiative(participant.getInitiative() + declaration.bonus());
        participant.setInitiativeDetails(participant.getInitiativeDetails() + " + " + declaration.label() + " " + declaration.bonus());
        sortByInitiative(encounter);
    }

    /**
     * Проверка навыка участника из его строки: итог остаётся там же и в журнале.
     *
     * @param skill код навыка или {@code custom:<id>} для навыка со своим названием
     * @throws IllegalArgumentException если такого навыка нет
     */
    @Transactional
    public SkillCheck check(Long campaignId, Long participantId, String skill, Integer difficulty, int modifier) {
        Character character = participant(campaignId, participantId).getCharacter();
        return skill.startsWith(CUSTOM_SKILL)
                ? skillService.check(character.getId(), null, Long.valueOf(skill.substring(CUSTOM_SKILL.length())), modifier, difficulty)
                : skillService.check(character.getId(), skill, null, modifier, difficulty);
    }

    /**
     * Снять эффект с участника: огонь потушили.
     */
    @Transactional
    public void removeEffect(Long campaignId, Long participantId, Long effectId) {
        participant(campaignId, participantId).getEffects().removeIf(effect -> effect.getId().equals(effectId));
    }

    /**
     * Инициатива вручную: игрок бросил свой кубик. Очередь пересчитывается.
     */
    @Transactional
    public void setInitiative(Long campaignId, Long participantId, int initiative) {
        EncounterParticipant participant = participant(campaignId, participantId);
        participant.setInitiative(initiative);
        participant.setInitiativeDetails("Введено вручную");
        sortByInitiative(participant.getEncounter());
    }

    /**
     * Персонаж участника: в его строке показывается итог действия.
     */
    @Transactional(readOnly = true)
    public Long characterOf(Long campaignId, Long participantId) {
        return participant(campaignId, participantId).getCharacter().getId();
    }

    /**
     * Урон участнику, не от атаки в приложении.
     *
     * @param location код зоны; пусто — система бросает сама
     * @throws IllegalArgumentException если у персонажа нет здоровья
     */
    @Transactional
    public HealthEvent damage(Long campaignId, Long participantId, int amount, String location, String damageType) {
        Character character = participant(campaignId, participantId).getCharacter();
        DamageRequest request = DamageRequest.builder()
                .amount(amount)
                .location(location == null || location.isBlank() ? null : location)
                .damageType(damageType == null || damageType.isBlank() ? null : damageType)
                .build();
        return healthCounterService.applyDamage(character.getId(), request)
                .orElseThrow(() -> new IllegalArgumentException("У персонажа нет счётчика здоровья"));
    }

    /**
     * Спасбросок участника, кубик бросает сервер.
     */
    @Transactional
    public HealthEvent save(Long campaignId, Long participantId, String code) {
        Character character = participant(campaignId, participantId).getCharacter();
        return healthCounterService.save(character.getId(), healthCounter(character), code, null);
    }

    /**
     * Медик стабилизировал участника.
     */
    @Transactional
    public HealthEvent stabilize(Long campaignId, Long participantId) {
        Character character = participant(campaignId, participantId).getCharacter();
        return healthCounterService.stabilize(character.getId(), healthCounter(character));
    }

    /**
     * Закончить бой. Раны, расход и броски в логе остаются.
     */
    @Transactional
    public void end(Long campaignId) {
        encounterRepository.delete(encounter(campaignId));
    }

    private Long healthCounter(Character character) {
        Long counterId = system(character.getCampaign()).getCombatCard(character).healthCounterId();
        if (counterId == null) {
            throw new IllegalArgumentException("У персонажа нет счётчика здоровья");
        }
        return counterId;
    }

    /** Эффекты идут до нового броска инициативы: ожог и оглушение от него влияют на этот раунд */
    private List<String> effectsOfNewRound(Encounter encounter, GameSystem system) {
        List<String> report = new ArrayList<>();
        for (EncounterParticipant participant : ordered(encounter)) {
            Character character = participant.getCharacter();
            for (CombatEffect effect : List.copyOf(participant.getEffects())) {
                if (system.getCombatCard(character).out()) {
                    participant.getEffects().clear();
                    break;
                }
                String roll = effect.nextRound();
                if (roll != null) {
                    report.add(character.getName() + ", " + effect.getLabel().toLowerCase() + ": " + attackService.effectDamage(character, effect.getLabel(), roll, effect.getData()));
                }
                if (effect.isOver()) {
                    participant.getEffects().remove(effect);
                    report.add(character.getName() + ": «" + effect.getLabel() + "» закончилось");
                }
            }
        }
        return report;
    }

    /** Новый раунд: все снова ходят; очередь бросается заново, если так велит система */
    private void startRound(Encounter encounter, GameSystem system) {
        encounter.setRound(encounter.getRound() + 1);
        RoundDice dice = new RoundDice(encounter);
        encounter.getParticipants().forEach(participant -> {
            participant.setDone(false);
            participant.setWaiting(false);
            participant.setDeclaration(null);
            if (system.rollsInitiativeEachRound()) {
                roll(participant, system, dice);
            }
        });
        if (system.rollsInitiativeEachRound()) {
            sortByInitiative(encounter);
        }
    }

    /** Первый, кто ещё не ходил; ждавшие получают ход, когда больше ходить некому */
    private EncounterParticipant current(Encounter encounter, GameSystem system) {
        List<EncounterParticipant> remaining = ordered(encounter).stream()
                .filter(participant -> !participant.isDone())
                .filter(participant -> !system.getCombatCard(participant.getCharacter()).out())
                .toList();
        return remaining.stream().filter(participant -> !participant.isWaiting()).findFirst()
                .orElse(remaining.isEmpty() ? null : remaining.getFirst());
    }

    /** Кто пойдёт после этого участника в этом раунде; null — раунд закончится */
    private EncounterParticipant nextAfter(Encounter encounter, EncounterParticipant current, GameSystem system) {
        return ordered(encounter).stream()
                .filter(participant -> participant != current && !participant.isDone())
                .filter(participant -> !system.getCombatCard(participant.getCharacter()).out())
                .findFirst().orElse(null);
    }

    private EncounterParticipant participant(Encounter encounter, Character character, GameSystem system) {
        EncounterParticipant participant = new EncounterParticipant();
        participant.setEncounter(encounter);
        participant.setCharacter(character);
        roll(participant, system, new RoundDice(encounter));
        return participant;
    }

    private void roll(EncounterParticipant participant, GameSystem system, RoundDice dice) {
        Initiative initiative = system.rollInitiative(participant.getCharacter(), dice.of(participant.getCharacter()));
        participant.setInitiative(initiative.total());
        participant.setInitiativeDetails(initiative.details());
    }

    /**
     * Кубики одного броска инициативы: сторона с общим кубиком получает его один раз на всех (книга, с. 97).
     * Правило броска остаётся у системы, ей достаточно подменить кубик.
     */
    private final class RoundDice {

        private final Encounter encounter;
        private final Map<String, Integer> shared = new HashMap<>();

        private RoundDice(Encounter encounter) {
            this.encounter = encounter;
        }

        private DiceRoller of(Character character) {
            boolean grouped = character.isNpc() ? encounter.isGroupNpcs() : encounter.isGroupPlayers();
            if (!grouped) {
                return diceRoller;
            }
            String side = character.isNpc() ? "npc" : "player";
            return sides -> shared.computeIfAbsent(side + sides, key -> diceRoller.roll(sides));
        }
    }

    /** HOMEBREW: при равной инициативе игроки ходят раньше NPC. В книге о равенстве ничего нет */
    private static void sortByInitiative(Encounter encounter) {
        renumber(encounter.getParticipants().stream()
                .sorted(Comparator.comparingInt(EncounterParticipant::getInitiative).reversed()
                        .thenComparing(participant -> participant.getCharacter().isNpc())
                        .thenComparing(participant -> participant.getCharacter().getId()))
                .toList());
    }

    private static void renumber(List<EncounterParticipant> order) {
        for (int i = 0; i < order.size(); i++) {
            order.get(i).setTurnOrder(i);
        }
    }

    private static List<EncounterParticipant> ordered(Encounter encounter) {
        return encounter.getParticipants().stream()
                .sorted(Comparator.comparingInt(EncounterParticipant::getTurnOrder))
                .toList();
    }

    private Encounter encounter(Long campaignId) {
        return encounterRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Бой уже закончен"));
    }

    private EncounterParticipant participant(Long campaignId, Long participantId) {
        return encounter(campaignId).getParticipants().stream()
                .filter(participant -> participant.getId().equals(participantId))
                .findFirst()
                .orElseThrow(NotFoundException.forId("EncounterParticipant", participantId));
    }

    private Campaign campaign(Long campaignId) {
        return campaignRepository.findById(campaignId).orElseThrow(NotFoundException.forId("Campaign", campaignId));
    }

    private Character member(Campaign campaign, Long characterId) {
        Character character = characterRepository.findById(characterId).orElseThrow(CharacterNotFoundException.forId(characterId));
        if (character.getCampaign() == null || !campaign.getId().equals(character.getCampaign().getId())) {
            throw new IllegalArgumentException("Участник должен быть из этой кампании: " + character.getName());
        }
        return character;
    }

    private GameSystem system(Campaign campaign) {
        return systemRegistry.find(campaign.getSystem()).orElseGet(systemRegistry::getGeneric);
    }
}
