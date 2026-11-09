package app.charka.encounter;

import app.charka.attack.AttackForm;
import app.charka.attack.AttackRules;
import app.charka.attack.AttackService;
import app.charka.attack.AttackSource;
import app.charka.check.CheckService;
import app.charka.check.OpposedCheckResult;
import app.charka.exception.CharacterNotFoundException;
import app.charka.exception.NotFoundException;
import app.charka.map.MapDoc;
import app.charka.map.MapGrid;
import app.charka.map.TokenPlacer;
import app.charka.model.BattleMap;
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
    private static final String INITIATIVE_BONUS = "initiativeBonus";

    private final EncounterRepository encounterRepository;
    private final app.charka.journal.JournalService journalService;
    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;
    private final GameSystemRegistry systemRegistry;
    private final DiceRoller diceRoller;
    private final HealthCounterService healthCounterService;
    private final AttackService attackService;
    private final SkillService skillService;
    private final CheckService checkService;

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
        return start(campaignId, characterIds, groupPlayers, groupNpcs, null);
    }

    /**
     * @param mapId карта боя кампании: бой играет на её копии, фишки расставляются сами; null — пустая карта (R-14)
     */
    @Transactional
    public Encounter start(Long campaignId, List<Long> characterIds, boolean groupPlayers, boolean groupNpcs, Long mapId) {
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
            // K-07: способности «раз за бой» — до инициативы
            system.onEncounterStart(participant.getCharacter());
            roll(participant, system, dice);
            encounter.getParticipants().add(participant);
        }
        sortByInitiative(encounter);
        if (mapId != null) {
            BattleMap map = campaign.getMaps().stream().filter(candidate -> candidate.getId().equals(mapId)).findFirst()
                    .orElseThrow(NotFoundException.forId("BattleMap", mapId));
            encounter.setMapDoc(map.getDoc());
            encounter.setBattleMapId(map.getId());
            TokenPlacer.place(encounter.getParticipants(), new MapGrid(MapDoc.parse(map.getDoc()), system.getMapRules()));
        } else {
            // R-14: боёв без карты нет — пустая карта, фишки встают сами
            MapDoc empty = MapDoc.empty(EMPTY_MAP_WIDTH, Math.max(EMPTY_MAP_HEIGHT, encounter.getParticipants().size()), system.getMapRules().cellMeters());
            encounter.setMapDoc(empty.toJson());
            TokenPlacer.place(encounter.getParticipants(), new MapGrid(empty, system.getMapRules()));
        }
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
            CombatCard card = system.getCombatCard(character);
            return new EncounterView.Row(participant, character, declaration(participant, system), available(participant, system), checks(character, system),
                    card, participant == current,
                    weapons(character, system), targets(character, fighters, system), attackService.pendingBlasts(character.getId()),
                    List.copyOf(participant.getEffects()), abilities(character, system),
                    RoundActions.done(participant, system.movementIsAction()),
                    system.getAttackRules() == null ? null : system.getAttackRules().actionOption(RoundActions.done(participant, system.movementIsAction())),
                    RoundActions.canAct(participant, system), healZones(character, card));
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

    /** Уже включённое не предлагается ещё раз */
    private static List<InitiativeDeclaration> available(EncounterParticipant participant, GameSystem system) {
        return system.getInitiativeDeclarations(participant.getCharacter()).stream()
                .filter(declaration -> participant.getEffects().stream().noneMatch(effect -> effect.getLabel().equals(effectLabel(declaration))))
                .toList();
    }

    private static String effectLabel(InitiativeDeclaration declaration) {
        return declaration.label() + " +" + declaration.bonus();
    }

    private static InitiativeDeclaration declaration(EncounterParticipant participant, GameSystem system) {
        return system.getInitiativeDeclarations(participant.getCharacter()).stream()
                .filter(declaration -> declaration.code().equals(participant.getDeclaration()))
                .findFirst().orElse(null);
    }

    /** Сначала то, что система считает нужным в бою; остальное — только изученное */
    private List<EncounterView.CheckOption> checks(Character character, GameSystem system) {
        return skillService.checkOptions(character.getId());
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
        int round = encounter.getRound();
        // J-01: новый раунд — запись журнала; урон горения и газа в его начале — её фишки
        return journalService.capture(() -> nextTurn(encounter), turn -> {
            if (encounter.getRound() != round) {
                return new app.charka.journal.JournalService.Draft(encounter.getCampaign(), null, app.charka.journal.JournalService.Kind.ROUND,
                        "Раунд " + encounter.getRound(), turn.report().isEmpty() ? "Начался" : String.join("; ", turn.report()),
                        null, null, null, turn.report(), List.of());
            }
            // кто ходит теперь — строкой ленты, а не «✓ Следующий ход»
            Character next = turn.character() == null ? null : encounter.getParticipants().stream()
                    .map(EncounterParticipant::getCharacter).filter(c -> c.getId().equals(turn.character())).findFirst().orElse(null);
            return next == null ? null : new app.charka.journal.JournalService.Draft(encounter.getCampaign(), next,
                    app.charka.journal.JournalService.Kind.TURN, "Ходит " + next.getName(), null, null, null, null, List.of(), List.of());
        });
    }

    private Turn nextTurn(Encounter encounter) {
        GameSystem system = system(encounter.getCampaign());
        EncounterParticipant current = current(encounter, system);
        if (current != null) {
            current.setDone(true);
            current.setWaiting(false);
        }
        sitOut(encounter, system);
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
     * K-07: рывок — действие раунда отдано на ещё одно перемещение (Космозэки: действие можно обменять, перемещение — нет).
     *
     * @throws IllegalArgumentException действие в этом раунде уже сделано
     */
    @Transactional
    public void dash(Long campaignId, Long participantId) {
        EncounterParticipant participant = participant(campaignId, participantId);
        if (participant.getActions() > 0) {
            throw new IllegalArgumentException("Действие в этом раунде уже сделано");
        }
        participant.setActions(participant.getActions() + 1);
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
        if (encounter.getMapDoc() != null) {
            TokenPlacer.place(encounter.getParticipants(), new MapGrid(MapDoc.parse(encounter.getMapDoc()), system.getMapRules()));
        }
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
        InitiativeDeclaration declaration = available(participant, system(encounter.getCampaign())).stream()
                .filter(known -> known.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Такого объявления нет: " + code));
        if (encounter.getParticipants().stream().anyMatch(EncounterParticipant::isDone)) {
            throw new IllegalArgumentException("Это объявляют в начале раунда, пока никто не походил");
        }
        if (declaration.rounds() > 0) {
            // на этот раунд ничего не меняет: прибавка начнётся со следующего броска инициативы, раунд + срок
            CombatEffect effect = new CombatEffect();
            effect.setParticipant(participant);
            effect.setLabel(effectLabel(declaration));
            effect.setRoundsLeft(declaration.rounds() + 1);
            effect.getData().put(INITIATIVE_BONUS, String.valueOf(declaration.bonus()));
            participant.getEffects().add(effect);
            return;
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
        return check(campaignId, participantId, skill, difficulty, modifier, 0);
    }

    @Transactional
    public SkillCheck check(Long campaignId, Long participantId, String skill, Integer difficulty, int modifier, int luck) {
        EncounterParticipant checking = participant(campaignId, participantId);
        Character character = checking.getCharacter();
        // A-05: проверка в бою — действие
        RoundActions.require(checking, system(checking.getEncounter().getCampaign()));
        checking.setActions(checking.getActions() + 1);
        return skill.startsWith(CUSTOM_SKILL)
                ? skillService.check(character.getId(), null, Long.valueOf(skill.substring(CUSTOM_SKILL.length())), modifier, difficulty, luck)
                : skillService.check(character.getId(), skill, null, modifier, difficulty, luck);
    }

    /**
     * Противостояние двух участников (CP2020: Facedown). Равенство — ничего; у проигравшего остаётся эффект системы.
     *
     * @param targetId персонаж противника, участник того же боя
     * @throws IllegalArgumentException если противостояния нет в системе или противник тот же
     */
    @Transactional
    public OpposedCheckResult contest(Long campaignId, Long participantId, String code, Long targetId) {
        return contest(campaignId, participantId, code, targetId, 0);
    }

    /**
     * Противостояние с удачей начавшего: тратится до броска.
     */
    @Transactional
    public OpposedCheckResult contest(Long campaignId, Long participantId, String code, Long targetId, int luck) {
        EncounterParticipant initiator = participant(campaignId, participantId);
        EncounterParticipant target = encounter(campaignId).getParticipants().stream()
                .filter(candidate -> candidate.getCharacter().getId().equals(targetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Противника нет в бою"));
        GameSystem system = systemRegistry.forCharacter(initiator.getCharacter());
        if (system.getCombatContests().stream().noneMatch(contest -> contest.code().equals(code))) {
            throw new IllegalArgumentException("Такого противостояния нет: " + code);
        }
        app.charka.check.CheckRequest own = system.getContestRequest(code, initiator.getCharacter());
        RoundActions.require(initiator, system);
        initiator.setActions(initiator.getActions() + 1);
        if (luck > 0) {
            system.spendLuck(initiator.getCharacter(), luck);
            own = own.withPart(GameSystem.luckPart(luck));
        }
        OpposedCheckResult result = checkService.checkOpposed(initiator.getCharacter(), own,
                target.getCharacter(), null, system.getContestRequest(code, target.getCharacter()));
        if (result.initiator().result().total() == result.opponent().result().total()
                || result.winner() == OpposedCheckResult.Winner.NONE) {
            return result;
        }
        boolean initiatorWon = result.winner() == OpposedCheckResult.Winner.INITIATOR;
        EncounterParticipant winner = initiatorWon ? initiator : target;
        EncounterParticipant loser = initiatorWon ? target : initiator;
        String label = system.getContestLoserEffect(code, winner.getCharacter());
        if (label != null && loser.getEffects().stream().noneMatch(effect -> effect.getLabel().equals(label))) {
            CombatEffect effect = new CombatEffect();
            effect.setParticipant(loser);
            effect.setLabel(label);
            effect.setRoundsLeft(CombatEffect.UNTIL_REMOVED);
            loser.getEffects().add(effect);
        }
        return result;
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
        return damage(campaignId, participantId, amount, location, damageType, 1, false);
    }

    /**
     * K-03: то же несколькими попаданиями ({@code hits}: броня вычитается из каждого) и оружием «броня ½».
     */
    @Transactional
    public HealthEvent damage(Long campaignId, Long participantId, int amount, String location, String damageType, int hits, boolean armorHalf) {
        Character character = participant(campaignId, participantId).getCharacter();
        Map<String, Integer> extras = new java.util.HashMap<>();
        if (hits > 1) {
            extras.put("hits", hits);
        }
        if (armorHalf) {
            extras.put("armorHalf", 1);
        }
        DamageRequest request = DamageRequest.builder()
                .amount(amount)
                .location(location == null || location.isBlank() ? null : location)
                .damageType(damageType == null || damageType.isBlank() ? null : damageType)
                .extras(extras)
                .build();
        return healthCounterService.applyDamage(character.getId(), request)
                .orElseThrow(() -> new IllegalArgumentException("У персонажа нет счётчика здоровья"));
    }

    /**
     * GM-01: мастер лечит участника с карты боя (не медпомощь: без броска и без действия).
     *
     * @param location зона, если система лечит по зонам; пусто — целиком
     * @throws IllegalArgumentException если у персонажа нет здоровья или очков меньше одного
     */
    @Transactional
    public HealthEvent heal(Long campaignId, Long participantId, int points, String location) {
        if (points < 1) {
            throw new IllegalArgumentException("Сколько вылечить — от 1");
        }
        Character character = participant(campaignId, participantId).getCharacter();
        return healthCounterService.heal(character.getId(), healthCounter(character), points, location);
    }

    /** Предел правки действий мастером: столько можно дать сверх или забрать за раунд */
    static final int ACTIONS_ADJUST_LIMIT = 5;

    /**
     * GM-01: мастер даёт участнику ещё действие в этом раунде ({@code delta} &lt; 0 — счётчик сделанных меньше)
     * или забирает ({@code delta} &gt; 0). Новый раунд обнуляет, как обычно. Запись в журнале боя.
     *
     * @return сколько действий сделано теперь
     */
    @Transactional
    public int adjustActions(Long campaignId, Long participantId, int delta) {
        EncounterParticipant participant = participant(campaignId, participantId);
        Character character = participant.getCharacter();
        int before = participant.getActions();
        int after = Math.max(-ACTIONS_ADJUST_LIMIT, Math.min(ACTIONS_ADJUST_LIMIT, before + delta));
        if (after == before) {
            throw new IllegalArgumentException(delta < 0 ? "Больше действий в этом раунде дать нельзя" : "Больше действий забрать нельзя");
        }
        return journalService.capture(() -> {
            participant.setActions(after);
            return after;
        }, done -> new app.charka.journal.JournalService.Draft(character.getCampaign(), character,
                app.charka.journal.JournalService.Kind.TURN,
                (after < before ? "Мастер дал действие: " : "Мастер забрал действие: ") + character.getName(),
                actionsLeft(participant, system(character.getCampaign())), null, null, null, List.of(), List.of()));
    }

    /** Сколько действий осталось в раунде — словами для журнала и итога */
    static String actionsLeft(EncounterParticipant participant, GameSystem system) {
        int limit = system.actionsPerRound();
        int done = RoundActions.done(participant, system.movementIsAction());
        if (limit <= 0) {
            return "Сделано действий в раунде: " + Math.max(0, done);
        }
        int left = Math.max(0, limit - done);
        return left == 0 ? "Действий в раунде не осталось" : "Осталось действий в раунде: " + left;
    }

    /** GM-01: зоны для лечения мастером — те же, что для урона, если система лечит по зонам */
    private List<CombatCard.Option> healZones(Character character, CombatCard card) {
        if (card.healthCounterId() == null || card.locations().isEmpty()) {
            return List.of();
        }
        return healthCounterService.healsByLocation(character.getId(), card.healthCounterId()) ? card.locations() : List.of();
    }

    /**
     * Спасбросок участника, кубик бросает сервер.
     */
    @Transactional
    public HealthEvent save(Long campaignId, Long participantId, String code) {
        return save(campaignId, participantId, code, 0);
    }

    /**
     * Спасбросок участника с удачей: очки тратятся до броска.
     *
     * @throws IllegalArgumentException если столько удачи нет
     */
    @Transactional
    public HealthEvent save(Long campaignId, Long participantId, String code, int luck) {
        Character character = participant(campaignId, participantId).getCharacter();
        if (luck > 0) {
            system(character.getCampaign()).spendLuck(character, luck);
        }
        return healthCounterService.save(character.getId(), healthCounter(character), code, null, Math.max(0, luck));
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
            app.charka.effect.TimedEffects.passRounds(character, 1)
                    .forEach(name -> report.add(character.getName() + ": «" + name + "» закончилось"));
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

    /**
     * Новый раунд: все снова ходят; очередь бросается заново, если так решила кампания (A-13). Без нового броска
     * прибавка объявления прошлого раунда (быстрое выхватывание +3) снимается: она была на один раунд.
     */
    private void startRound(Encounter encounter, GameSystem system) {
        encounter.setRound(encounter.getRound() + 1);
        RoundDice dice = new RoundDice(encounter);
        boolean reroll = encounter.getCampaign().isInitiativeEachRound();
        encounter.getParticipants().forEach(participant -> {
            if (!reroll) {
                if (participant.getDeclaration() != null) {
                    withdrawDeclaration(participant, system);
                }
                applyEffectBonus(participant);
            }
            participant.setDone(false);
            participant.setWaiting(false);
            participant.setDeclaration(null);
            participant.setMovedMeters(0);
            participant.setActions(0);
            if (reroll) {
                roll(participant, system, dice);
            }
        });
        sortByInitiative(encounter);
        sitOut(encounter, system);
    }

    /**
     * BF-01: выбывший (без сознания, мёртв) пропускает раунд целиком: ход прошёл мимо — он «походил», а действие
     * раунда потрачено. Вылечили в том же раунде — ходит и действует только со следующего; лишнее действие может дать
     * мастер (GM-01). HOMEBREW: решение автора, в правилах систем об этом нет.
     */
    private void sitOut(Encounter encounter, GameSystem system) {
        int limit = system.actionsPerRound();
        encounter.getParticipants().stream()
                .filter(participant -> !participant.isDone())
                .filter(participant -> system.getCombatCard(participant.getCharacter()).out())
                .forEach(participant -> {
                    participant.setDone(true);
                    participant.setWaiting(false);
                    if (limit > 0) {
                        participant.setActions(Math.max(participant.getActions(), limit));
                    }
                });
    }

    private static void withdrawDeclaration(EncounterParticipant participant, GameSystem system) {
        system.getInitiativeDeclarations(participant.getCharacter()).stream()
                .filter(declaration -> declaration.code().equals(participant.getDeclaration()))
                .findFirst()
                .ifPresent(declaration -> {
                    participant.setInitiative(participant.getInitiative() - declaration.bonus());
                    String tail = " + " + declaration.label() + " " + declaration.bonus();
                    String details = participant.getInitiativeDetails();
                    if (details != null && details.endsWith(tail)) {
                        participant.setInitiativeDetails(details.substring(0, details.length() - tail.length()));
                    }
                });
    }

    private static final java.util.regex.Pattern EFFECT_BONUS = java.util.regex.Pattern.compile(" [+] эффекты (-?[0-9]+)$");

    /**
     * Без нового броска прибавки эффектов к инициативе (Speedware +3 на 5 раундов) меняют её сами: то, что прибавлено
     * при прошлом броске или раунде, записано в конце расшифровки «+ эффекты N».
     */
    private static void applyEffectBonus(EncounterParticipant participant) {
        int bonus = participant.getEffects().stream()
                .mapToInt(effect -> Integer.parseInt(effect.getData().getOrDefault(INITIATIVE_BONUS, "0")))
                .sum();
        String details = participant.getInitiativeDetails() == null ? "" : participant.getInitiativeDetails();
        java.util.regex.Matcher applied = EFFECT_BONUS.matcher(details);
        int old = applied.find() ? Integer.parseInt(applied.group(1)) : 0;
        if (bonus == old) {
            return;
        }
        String base = applied.find(0) ? details.substring(0, applied.start()) : details;
        participant.setInitiative(participant.getInitiative() - old + bonus);
        participant.setInitiativeDetails(base + (bonus != 0 ? " + эффекты " + bonus : ""));
    }

    /** A-13: мастер переключает, бросать ли инициативу каждый раунд; действует с нового раунда */
    @Transactional
    public void setInitiativeEachRound(Long campaignId, boolean eachRound) {
        campaign(campaignId).setInitiativeEachRound(eachRound);
    }

    /** M-16: туман для игроков — выбор кампании */
    @Transactional
    public void setFogOfWar(Long campaignId, boolean on) {
        campaign(campaignId).setFogOfWar(on);
    }

    /** R-14: пустая карта, если мастер не выбрал: клеток с запасом на участников */
    public static final int EMPTY_MAP_WIDTH = 24;
    public static final int EMPTY_MAP_HEIGHT = 16;

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
        system.onEncounterStart(character);
        roll(participant, system, new RoundDice(encounter));
        return participant;
    }

    private void roll(EncounterParticipant participant, GameSystem system, RoundDice dice) {
        Initiative initiative = system.rollInitiative(participant.getCharacter(), dice.of(participant.getCharacter()));
        int bonus = participant.getEffects().stream()
                .mapToInt(effect -> Integer.parseInt(effect.getData().getOrDefault(INITIATIVE_BONUS, "0")))
                .sum();
        participant.setInitiative(initiative.total() + bonus);
        participant.setInitiativeDetails(initiative.details() + (bonus != 0 ? " + эффекты " + bonus : ""));
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
