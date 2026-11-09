package app.charka.map;

import app.charka.encounter.EncounterService;
import app.charka.encounter.EncounterView;
import app.charka.exception.NotFoundException;
import app.charka.model.BattleMap;
import app.charka.model.Encounter;
import app.charka.model.EncounterParticipant;
import app.charka.repository.EncounterRepository;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Поле идущего боя: копия карты и фишки участников. Правила атаки не меняются — поле только подсказывает
 * цель, расстояние и укрытие тому же диалогу атаки.
 */
@Service
@RequiredArgsConstructor
public class BattleFieldService {

    private final EncounterRepository encounterRepository;
    private final EncounterService encounterService;
    private final BattleMapService battleMapService;
    private final GameSystemRegistry systemRegistry;
    private final PointerBoard pointerBoard;
    private final app.charka.npc.NpcService npcService;
    private final app.charka.repository.MapLootRepository mapLootRepository;
    private final app.charka.encounter.RoundActions roundActions;

    /**
     * Фишка участника.
     *
     * @param id       участник боя
     * @param x        клетка; null — в лотке «Не на карте»
     * @param initials буквы на фишке
     * @param current  его ход
     * @param out      выбыл
     * @param alarming состояние требует внимания: красное кольцо
     * @param moved    сколько метров пробежал в этом раунде
     * @param actionOption модификатор атаки за следующее действие в раунде (A-05: после бега и выстрела); null — нет
     * @param level        M-22: уровень карты, 0 — нижний
     */
    public record Token(Long id, Long character, String name, String initials, boolean npc, Integer x, Integer y,
                        boolean current, boolean done, boolean out, boolean alarming, String status, int moved, String actionOption,
                        int level) {
    }

    /**
     * Всё, что рисует браузер. Меняется только от действий за столом: на этом держится ETag опроса.
     *
     * @param turn      раунд и чей ход: сменился — строки трекера устарели
     * @param doc       документ карты
     * @param metric    мера расстояния ({@link MapRules#metric})
     * @param materials материалы системы
     * @param objects   готовые предметы системы
     * @param fog       M-16: что видят персонажи игрока, по клетке на символ ({@code 1} — видно); null — туман не действует
     * @param fogLevels M-22: то же по уровням карты, {@code fog} — нижний; null — туман не действует
     */
    public record Field(Long mapId, String turn, MapDoc doc, String metric, List<Material> materials, List<MapObject> objects,
                        List<Token> tokens, List<PointerBoard.Pointer> pointers, String backgroundUrl, String fog, List<String> fogLevels,
                        List<Loot> loot) {
    }

    /**
     * KF-05: предмет на карте. Игроку — только на клетках, которые видят его персонажи (M-16).
     *
     * @param quantity сколько строк справочника
     */
    public record Loot(Long id, int x, int y, int level, String name, int quantity) {
    }

    /**
     * Кто смотрит на карту.
     *
     * @param mine персонажи игрока; null — мастер, видит всё
     */
    public record Viewer(Set<Long> mine) {

        public static final Viewer GM = new Viewer(null);

        public boolean gm() {
            return mine == null;
        }
    }

    /**
     * M-16: туман для игроков.
     *
     * @param hidden участники, которых игрок не видит
     * @param cells  клетки, видимые хоть одному его персонажу, по уровням карты; null — тумана нет
     */
    private record Fog(Set<Long> hidden, List<String> cells) {

        static final Fog NONE = new Fog(Set.of(), null);

        String ground() {
            return cells == null ? null : cells.get(0);
        }
    }

    /**
     * Что скрыто от игрока. Свои и персонажи других игроков видны всегда (HOMEBREW: команда знает, где свои),
     * NPC — только если его клетку видит хоть один персонаж игрока на карте хотя бы краем.
     */
    private Fog fog(Encounter encounter, Viewer viewer) {
        if (viewer.gm() || !encounter.getCampaign().isFogOfWar()) {
            return Fog.NONE;
        }
        LevelMap levels = levels(encounter);
        int size = levels.doc().w() * levels.doc().h();
        boolean[][] seen = new boolean[levels.levels()][size];
        encounter.getParticipants().stream()
                .filter(p -> p.getPosX() != null && viewer.mine().contains(p.getCharacter().getId()) && levels.doc().hasLevel(p.getPosLevel()))
                .forEach(eye -> {
                    // M-22: со своего уровня и вниз сквозь пустоту
                    boolean[][] visible = levels.visibleFrom(eye.getPosLevel(), eye.getPosX(), eye.getPosY());
                    for (int l = 0; l < seen.length; l++) {
                        for (int i = 0; i < size; i++) {
                            seen[l][i] |= visible[l][i];
                        }
                    }
                });
        Set<Long> hidden = new HashSet<>();
        for (EncounterParticipant p : encounter.getParticipants()) {
            boolean ally = !p.getCharacter().isNpc() || viewer.mine().contains(p.getCharacter().getId());
            boolean placed = p.getPosX() != null && levels.doc().hasLevel(p.getPosLevel());
            if (!ally && (!placed || !seen[p.getPosLevel()][p.getPosY() * levels.doc().w() + p.getPosX()])) {
                hidden.add(p.getId());
            }
        }
        List<String> cells = new java.util.ArrayList<>();
        for (boolean[] level : seen) {
            StringBuilder line = new StringBuilder(size);
            for (boolean visible : level) {
                line.append(visible ? '1' : '0');
            }
            cells.add(line.toString());
        }
        return new Fog(hidden, cells);
    }

    /**
     * J-01: имена участников боя для ленты: {@code true} — игрок его не видит, в ленте он «?». Нет скрытых — пусто.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Boolean> fogNames(Long campaignId, Viewer viewer) {
        return encounterRepository.findByCampaignId(campaignId)
                .filter(encounter -> encounter.getMapDoc() != null)
                .map(encounter -> {
                    Set<Long> hidden = fog(encounter, viewer).hidden();
                    if (hidden.isEmpty()) {
                        return java.util.Map.<String, Boolean>of();
                    }
                    java.util.Map<String, Boolean> names = new java.util.HashMap<>();
                    // одно имя у видимого и скрытого — не прячем: видимый важнее
                    encounter.getParticipants().forEach(p -> names.merge(p.getCharacter().getName(), hidden.contains(p.getId()), Boolean::logicalAnd));
                    return names;
                })
                .orElse(java.util.Map.of());
    }

    /**
     * M-16: участники, которых игрок сейчас не видит; мастеру, без карты или без тумана — пусто.
     */
    @Transactional(readOnly = true)
    public Set<Long> hiddenFor(Long campaignId, Viewer viewer) {
        return encounterRepository.findByCampaignId(campaignId)
                .filter(encounter -> encounter.getMapDoc() != null)
                .map(encounter -> fog(encounter, viewer).hidden())
                .orElse(Set.of());
    }

    /**
     * @return пусто — боя нет или он без карты
     */
    @Transactional(readOnly = true)
    public Optional<Field> field(Long campaignId) {
        return field(campaignId, Viewer.GM);
    }

    /**
     * @param viewer игроку не отдаются фишки в тумане (M-16)
     */
    @Transactional(readOnly = true)
    public Optional<Field> field(Long campaignId, Viewer viewer) {
        Optional<EncounterView> view = encounterService.view(campaignId);
        if (view.isEmpty() || view.get().encounter().getMapDoc() == null) {
            return Optional.empty();
        }
        Encounter encounter = view.get().encounter();
        MapRules rules = rules(encounter);
        Fog fog = fog(encounter, viewer);
        List<Token> tokens = view.get().rows().stream().filter(row -> !fog.hidden().contains(row.participant().getId())).map(row -> {
            EncounterParticipant participant = row.participant();
            return new Token(participant.getId(), row.character().getId(), row.character().getName(), initials(row.character().getName()),
                    row.character().isNpc(), participant.getPosX(), participant.getPosY(), row.current(), participant.isDone(),
                    row.card().out(), row.card().alarming(), row.card().status(), participant.getMovedMeters(), row.actionOption(),
                    participant.getPosLevel());
        }).toList();
        String turn = encounter.getRound() + ":" + (view.get().current() == null ? "" : view.get().current().getId());
        MapDoc doc = MapDoc.parse(encounter.getMapDoc());
        List<Loot> loot = mapLootRepository.findByEncounterIdOrderByIdAsc(encounter.getId()).stream()
                .filter(item -> fog.cells() == null || seen(fog.cells(), doc.w(), item.getPosLevel(), item.getPosX(), item.getPosY()))
                .map(item -> new Loot(item.getId(), item.getPosX(), item.getPosY(), item.getPosLevel(), item.getName(), item.getQuantity()))
                .toList();
        return Optional.of(new Field(encounter.getBattleMapId(), turn, doc, rules.metric(), rules.materials(), rules.objects(), tokens,
                pointerBoard.live(campaignId), backgroundUrl(campaignId, encounter.getBattleMapId(), doc), fog.ground(), fog.cells(),
                loot));
    }

    /**
     * Подложка поля боя — адресом боя, а не карты: раздел «Карты» — заготовки мастера, игроку закрыт, и картинка
     * у него не грузилась (пустая карта). Версия в адресе — для долгого кэша; null — подложки нет.
     */
    static String backgroundUrl(Long campaignId, Long mapId, MapDoc doc) {
        return doc.bg() == null || mapId == null ? null
                : "/campaign/" + campaignId + "/encounter/map/background?v=" + doc.bg().ver();
    }

    /**
     * Картинка подложки идущего боя — всем за столом.
     *
     * @return пусто — боя, карты-источника или картинки нет
     */
    @Transactional(readOnly = true)
    public Optional<app.charka.model.BattleMap> background(Long campaignId) {
        return encounterRepository.findByCampaignId(campaignId)
                .map(Encounter::getBattleMapId)
                .flatMap(mapId -> {
                    try {
                        return Optional.of(battleMapService.get(campaignId, mapId));
                    } catch (java.util.NoSuchElementException e) {
                        return Optional.empty();
                    }
                })
                .filter(map -> map.getBackground() != null);
    }

    /** M-16: видна ли клетка игроку по строкам тумана */
    private static boolean seen(List<String> cells, int w, int level, int x, int y) {
        if (level < 0 || level >= cells.size()) {
            return false;
        }
        int index = y * w + x;
        String line = cells.get(level);
        return index >= 0 && index < line.length() && line.charAt(index) == '1';
    }

    public void move(Long campaignId, Long participantId, int x, int y) {
        move(campaignId, participantId, x, y, false);
    }

    /**
     * Перенести фишку. Бег за раунд — не дальше досягаемости системы (CP2020: MA × 3 м, с. 98), путь — в обход стен и
     * закрытых дверей; пройденное копится до нового раунда. Фишка из лотка встаёт куда угодно.
     *
     * @param free мастер ставит без ограничения и не тратит бег (расстановка, отброс взрывом)
     * @throws IllegalArgumentException если клетка за краем, в предмете, занята или дальше оставшегося бега
     */
    @Transactional
    public void move(Long campaignId, Long participantId, int x, int y, boolean free) {
        move(campaignId, participantId, x, y, free, null);
    }

    /**
     * M-22: то же на уровне карты. Уровень меняют только фишка из лотка и мастер без ограничения бега; остальные — по лестнице
     * ({@link #climb}).
     *
     * @param level уровень; null — тот же, где фишка
     * @throws IllegalArgumentException если уровня нет или фишка на карте переходит на другой уровень не по лестнице
     */
    @Transactional
    public void move(Long campaignId, Long participantId, int x, int y, boolean free, Integer level) {
        Encounter encounter = withMap(campaignId);
        EncounterParticipant participant = participant(encounter, participantId);
        // BF-02: фишку на карте игрок двигает на своём ходу; из лотка ставит когда угодно
        if (participant.getPosX() != null) {
            roundActions.requireTurn(participant);
        }
        LevelMap levels = levels(encounter);
        int target = level == null ? participant.getPosLevel() : level;
        if (!levels.doc().hasLevel(target)) {
            throw new IllegalArgumentException("Нет такого уровня карты");
        }
        if (target != participant.getPosLevel() && participant.getPosX() != null && !free) {
            throw new IllegalArgumentException("На другой уровень — по лестнице");
        }
        if (target != participant.getPosLevel()) {
            participant.setPosLevel(target);
            participant.setPosX(null);
            participant.setPosY(null);
        }
        MapGrid grid = levels.grid(target);
        if (!grid.doc().inside(x, y)) {
            throw new IllegalArgumentException("За краем карты");
        }
        if (grid.blocked(x, y)) {
            throw new IllegalArgumentException("Туда не встать: " + grid.material(x, y).map(Material::label).orElse("занято"));
        }
        if (taken(encounter, participant, target, x, y)) {
            throw new IllegalArgumentException("Клетка занята");
        }
        Integer run = free || participant.getPosX() == null ? null : runOf(encounter, participant);
        if (run != null) {
            int left = Math.max(0, run - participant.getMovedMeters());
            int cost = grid.reach(participant.getPosX(), participant.getPosY(), left).stream()
                    .filter(cell -> cell[0] == x && cell[1] == y).mapToInt(cell -> cell[2]).findFirst().orElse(-1);
            if (cost < 0) {
                throw new IllegalArgumentException(left == 0 ? "Бег в этом раунде уже весь: " + run + " м"
                        : "Дальше бега: осталось " + left + " из " + run + " м");
            }
            participant.setMovedMeters(participant.getMovedMeters() + cost);
        }
        participant.setPosX(x);
        participant.setPosY(y);
    }

    /** Бег за раунд по правилам системы; null — система его не знает, ограничения нет */
    private Integer runOf(Encounter encounter, EncounterParticipant participant) {
        GameSystem system = systemRegistry.get(encounter.getCampaign().getSystem());
        return system.getMapRules().reach(system.getCurrentStats(participant.getCharacter()));
    }

    /**
     * Инструмент мастера «＋ Фишка»: персонаж кампании или новый NPC по шаблону встаёт в бой сразу на эту клетку.
     * Уже в бою — фишка просто переносится сюда.
     *
     * @param characterId персонаж кампании; null — новый NPC
     * @param template    шаблон системы для нового NPC; пусто — пустой NPC
     * @return кто встал
     * @throws IllegalArgumentException если клетка за краем, в предмете или занята
     */
    @Transactional
    public String addToken(Long campaignId, Long characterId, String template, int x, int y) {
        return addToken(campaignId, characterId, template, x, y, 0);
    }

    /**
     * @param level M-22: уровень карты
     */
    @Transactional
    public String addToken(Long campaignId, Long characterId, String template, int x, int y, int level) {
        Encounter encounter = withMap(campaignId);
        MapGrid grid = levels(encounter).grid(level);
        if (!grid.doc().inside(x, y)) {
            throw new IllegalArgumentException("За краем карты");
        }
        if (grid.blocked(x, y)) {
            throw new IllegalArgumentException("Туда не встать: " + grid.material(x, y).map(Material::label).orElse("занято"));
        }
        Long id = characterId != null ? characterId
                : npcService.create(campaignId, template == null ? "" : template, null, 1, false).get(0).getId();
        EncounterParticipant participant = encounter.getParticipants().stream()
                .filter(p -> p.getCharacter().getId().equals(id)).findFirst()
                .orElseGet(() -> {
                    encounterService.add(campaignId, id);
                    return encounter.getParticipants().stream().filter(p -> p.getCharacter().getId().equals(id)).findFirst().orElseThrow();
                });
        if (taken(encounter, participant, level, x, y)) {
            throw new IllegalArgumentException("Клетка занята");
        }
        participant.setPosLevel(level);
        participant.setPosX(x);
        participant.setPosY(y);
        return participant.getCharacter().getName();
    }

    /** Убрать фишку с поля в лоток */
    @Transactional
    public void lift(Long campaignId, Long participantId) {
        EncounterParticipant participant = participant(withMap(campaignId), participantId);
        participant.setPosX(null);
        participant.setPosY(null);
    }

    /**
     * Куда участник ещё добежит в этом раунде (CP2020: бег MA × 3 м), не сквозь стены и закрытые двери.
     *
     * @param meters сколько бега осталось; null — система не знает, область не рисуется
     * @param run    весь бег за раунд
     * @param cells  клетки {@code {x, y, метров пути}}
     */
    public record Reach(Integer meters, Integer run, List<int[]> cells) {
    }

    @Transactional(readOnly = true)
    public Reach reach(Long campaignId, Long participantId) {
        Encounter encounter = withMap(campaignId);
        EncounterParticipant participant = participant(encounter, participantId);
        GameSystem system = systemRegistry.get(encounter.getCampaign().getSystem());
        Integer run = system.getMapRules().reach(system.getCurrentStats(participant.getCharacter()));
        if (run == null || participant.getPosX() == null) {
            return new Reach(run, run, List.of());
        }
        int left = Math.max(0, run - participant.getMovedMeters());
        return new Reach(left, run, levels(encounter).grid(participant.getPosLevel()).reach(participant.getPosX(), participant.getPosY(), left));
    }

    /**
     * Что между двумя фишками: для меню и диалога атаки.
     *
     * @param meters       расстояние по мере системы
     * @param blocked      цель не видна
     * @param cover        код укрытия цели (вариант модификатора атаки); при стрельбе вслепую — преграда; null — не за укрытием
     * @param coverLabel   название укрытия
     * @param visibility   {@code full} — видна целиком, {@code partial} — частично (из-за угла, в проёме), {@code none} — не видна
     * @param exposed      сколько из {@link MapGrid#SAMPLES} точек клетки цели видно
     * @param throughLabel цель не видна, но стрелять можно вслепую сквозь эту преграду; null — нельзя
     * @param blindOption  модификатор стрельбы вслепую системы ({@link MapRules#blindFireOption}); null — нет
     * @param inTheWay     кто стоит на линии огня
     */
    public record Sight(int meters, boolean blocked, String cover, String coverLabel, String visibility, int exposed,
                        String throughLabel, String blindOption, List<String> inTheWay) {

        /** Выстрел возможен: цель видна или есть одна пробиваемая преграда */
        public boolean canShoot() {
            return !blocked || throughLabel != null;
        }
    }

    /** HOMEBREW: низкий предмет укрывает цель, если он не дальше клетки от неё — в книге способа нет */
    static final int COVER_NEAR_CELLS = 1;

    @Transactional(readOnly = true)
    public Sight sight(Long campaignId, Long fromParticipant, Long toParticipant) {
        return sight(campaignId, fromParticipant, toParticipant, Viewer.GM);
    }

    /**
     * @throws IllegalArgumentException если фишки нет на карте или игрок её не видит (M-16)
     */
    @Transactional(readOnly = true)
    public Sight sight(Long campaignId, Long fromParticipant, Long toParticipant, Viewer viewer) {
        Encounter encounter = withMap(campaignId);
        EncounterParticipant from = participant(encounter, fromParticipant);
        EncounterParticipant to = participant(encounter, toParticipant);
        if (from.getPosX() == null || to.getPosX() == null) {
            throw new IllegalArgumentException("Фишка не на карте");
        }
        if (fog(encounter, viewer).hidden().contains(to.getId())) {
            throw new IllegalArgumentException("Цель не видна");
        }
        return sight(encounter, levels(encounter), rules(encounter), from, to);
    }

    private Sight sight(Encounter encounter, LevelMap levels, MapRules rules, EncounterParticipant from, EncounterParticipant to) {
        int meters = levels.distance(from.getPosLevel(), from.getPosX(), from.getPosY(), to.getPosLevel(), to.getPosX(), to.getPosY());
        Optional<MapGrid.Sight> between = levels.sight(from.getPosLevel(), from.getPosX(), from.getPosY(),
                to.getPosLevel(), to.getPosX(), to.getPosY(), COVER_NEAR_CELLS);
        if (between.isEmpty()) {
            // M-22: уровни не видят друг друга здесь — пол или перекрытие между ними
            return new Sight(meters, true, null, null, MapGrid.Visibility.NONE.name().toLowerCase(), 0, null, null, List.of());
        }
        MapGrid.Sight sight = between.get();
        boolean hidden = sight.visibility() == MapGrid.Visibility.NONE;
        // вслепую — только если система знает такой модификатор
        Optional<Material> through = rules.blindFireOption() == null ? Optional.empty() : sight.through();
        Optional<Material> cover = hidden ? through : sight.cover();
        // M-23: сквозь дым — вслепую; преграда за ним, если есть, остаётся укрытием
        String throughLabel = through.map(material -> material.label() + (material.sp() > 0 ? " SP " + material.sp() : "")).orElse(null);
        if (hidden && sight.smoke() && rules.blindFireOption() != null) {
            throughLabel = throughLabel == null ? "дым" : "дым и " + throughLabel;
        }
        List<String> inTheWay = encounter.getParticipants().stream()
                .filter(p -> p != from && p != to && p.getPosX() != null && p.getPosLevel() == from.getPosLevel())
                .filter(p -> sight.path().stream().anyMatch(cell -> cell[0] == p.getPosX() && cell[1] == p.getPosY()))
                .map(p -> p.getCharacter().getName()).toList();
        return new Sight(meters, hidden, cover.map(Material::code).orElse(null), cover.map(Material::label).orElse(null),
                sight.visibility().name().toLowerCase(), sight.exposed(),
                throughLabel, throughLabel != null ? rules.blindFireOption() : null, inTheWay);
    }

    /**
     * Что видит участник: поле видимости и каждая другая фишка.
     *
     * @param w       ширина карты: строка {@code cells} идёт по строкам карты
     * @param cells   по клетке на символ: {@code 1} — видна хотя бы краем, {@code 0} — нет
     * @param targets остальные фишки на карте
     */
    public record View(int w, String cells, List<TargetView> targets) {
    }

    /**
     * @param id         участник боя
     * @param visibility как в {@link Sight#visibility}
     * @param canShoot   можно стрелять (видна или вслепую сквозь одну преграду)
     * @param cover      название укрытия; null — нет
     */
    public record TargetView(Long id, String visibility, boolean canShoot, String cover) {
    }

    @Transactional(readOnly = true)
    public View view(Long campaignId, Long participantId) {
        return view(campaignId, participantId, Viewer.GM);
    }

    /**
     * @throws IllegalArgumentException если игрок смотрит глазами чужого персонажа (M-16)
     */
    @Transactional(readOnly = true)
    public View view(Long campaignId, Long participantId, Viewer viewer) {
        Encounter encounter = withMap(campaignId);
        EncounterParticipant from = participant(encounter, participantId);
        if (!viewer.gm() && !viewer.mine().contains(from.getCharacter().getId())) {
            throw new IllegalArgumentException("Поле видимости — только своих персонажей");
        }
        LevelMap levels = levels(encounter);
        MapGrid grid = levels.grid(levels.doc().hasLevel(from.getPosLevel()) ? from.getPosLevel() : 0);
        Set<Long> hidden = fog(encounter, viewer).hidden();
        if (from.getPosX() == null) {
            return new View(grid.doc().w(), "", List.of());
        }
        MapRules rules = rules(encounter);
        StringBuilder cells = new StringBuilder();
        for (boolean visible : grid.visibleFrom(from.getPosX(), from.getPosY())) {
            cells.append(visible ? '1' : '0');
        }
        List<TargetView> targets = encounter.getParticipants().stream()
                .filter(p -> p != from && p.getPosX() != null && !hidden.contains(p.getId()))
                .map(p -> {
                    Sight sight = sight(encounter, levels, rules, from, p);
                    return new TargetView(p.getId(), sight.visibility(), sight.canShoot(), sight.coverLabel());
                }).toList();
        return new View(grid.doc().w(), cells.toString(), targets);
    }

    /**
     * Открыть или закрыть дверь в копии карты боя.
     *
     * @throws IllegalArgumentException если на грани не дверь
     */
    @Transactional
    public void toggleDoor(Long campaignId, String edgeKey) {
        toggleDoor(campaignId, edgeKey, 0);
    }

    /**
     * @param level M-22: уровень карты
     */
    @Transactional
    public void toggleDoor(Long campaignId, String edgeKey, int level) {
        Encounter encounter = withMap(campaignId);
        MapDoc doc = MapDoc.parse(encounter.getMapDoc());
        MapDoc view = doc.level(level);
        MapDoc.Edge edge = view.edge(edgeKey);
        if (edge == null || edge.kind() != MapDoc.Edge.Kind.DOOR) {
            throw new IllegalArgumentException("Здесь нет двери");
        }
        view.walls().put(edgeKey, new MapDoc.Edge(edge.kind(), edge.material(), !edge.open()).toString());
        encounter.setMapDoc(doc.withLevel(level, view).toJson());
    }

    /**
     * M-23: выбить дверь, разбить окно — грань становится проёмом. Стену ломает только мастер (или взрыв, {@link MapBlastService}).
     * HOMEBREW: в книге нет правила, как выбить дверь; бросок, если нужен, мастер просит отдельно.
     *
     * @param gm ломает мастер
     * @throws IllegalArgumentException если грани нет или игрок ломает стену
     */
    @Transactional
    public String breakEdge(Long campaignId, String edgeKey, boolean gm) {
        return breakEdge(campaignId, edgeKey, gm, 0);
    }

    /**
     * @param level M-22: уровень карты
     */
    @Transactional
    public String breakEdge(Long campaignId, String edgeKey, boolean gm, int level) {
        Encounter encounter = withMap(campaignId);
        MapDoc whole = MapDoc.parse(encounter.getMapDoc());
        MapDoc doc = whole.level(level);
        MapDoc.Edge edge = doc.edge(edgeKey);
        if (edge == null || edge.kind() == MapDoc.Edge.Kind.GAP) {
            throw new IllegalArgumentException("Здесь нечего ломать");
        }
        if (edge.kind() == MapDoc.Edge.Kind.WALL && !gm) {
            throw new IllegalArgumentException("Стену ломает мастер — или взрыв");
        }
        doc.walls().put(edgeKey, "gap");
        encounter.setMapDoc(whole.withLevel(level, doc).toJson());
        String material = edge.material() == null ? "" : rules(encounter).material(edge.material()).map(m -> " (" + m.label() + ")").orElse("");
        return switch (edge.kind()) {
            case DOOR -> "Дверь выбита" + material;
            case WINDOW -> "Окно разбито" + material + ": можно пролезть";
            default -> "Стена проломлена" + material;
        };
    }

    /**
     * Мастер правит копию карты боя (M-11): взорвали стену, сдвинули ящик.
     */
    @Transactional
    public void updateDoc(Long campaignId, String json) {
        Encounter encounter = withMap(campaignId);
        MapDoc doc = MapDoc.parse(json);
        encounter.setMapDoc(doc.toJson());
        // фишки за новым краем, в новом предмете или на удалённом уровне уходят в лоток
        LevelMap levels = levels(encounter);
        encounter.getParticipants().stream()
                .filter(p -> p.getPosX() != null
                        && (!levels.doc().hasLevel(p.getPosLevel()) || levels.grid(p.getPosLevel()).blocked(p.getPosX(), p.getPosY())))
                .forEach(p -> {
                    p.setPosX(null);
                    p.setPosY(null);
                    p.setPosLevel(0);
                });
    }

    @Transactional(readOnly = true)
    public EditableMap editable(Long campaignId) {
        Encounter encounter = withMap(campaignId);
        return EditableMap.of(campaignId, encounter.getBattleMapId(), "Карта боя", MapDoc.parse(encounter.getMapDoc()), rules(encounter));
    }

    /** «Сохранить как новую карту»: копия боя становится заготовкой */
    @Transactional
    public BattleMap saveAsMap(Long campaignId, String name) {
        return battleMapService.save(campaignId, name, MapDoc.parse(withMap(campaignId).getMapDoc()));
    }

    /**
     * Положить карту под идущий бой: копия документа, фишки расставляются заново.
     */
    @Transactional
    public void attach(Long campaignId, Long mapId) {
        Encounter encounter = encounterRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Боя нет"));
        BattleMap map = battleMapService.get(campaignId, mapId);
        encounter.setMapDoc(map.getDoc());
        encounter.setBattleMapId(map.getId());
        encounter.getParticipants().forEach(p -> {
            p.setPosX(null);
            p.setPosY(null);
            p.setPosLevel(0);
        });
        TokenPlacer.place(encounter.getParticipants(), grid(encounter));
    }

    /** R-14: пустая карта под идущий бой — как у боя, начатого без карты */
    @Transactional
    public void attachEmpty(Long campaignId) {
        Encounter encounter = encounterRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Боя нет"));
        MapDoc empty = MapDoc.empty(EncounterService.EMPTY_MAP_WIDTH,
                Math.max(EncounterService.EMPTY_MAP_HEIGHT, encounter.getParticipants().size()), rules(encounter).cellMeters());
        encounter.setMapDoc(empty.toJson());
        encounter.setBattleMapId(null);
        encounter.getParticipants().forEach(p -> {
            p.setPosX(null);
            p.setPosY(null);
            p.setPosLevel(0);
        });
        TokenPlacer.place(encounter.getParticipants(), grid(encounter));
    }

    /** Указатель для всех (M-13) */
    public PointerBoard.Pointer point(Long campaignId, int x, int y, Integer fromX, Integer fromY, String who) {
        Encounter encounter = withMap(campaignId);
        if (!MapDoc.parse(encounter.getMapDoc()).inside(x, y)) {
            throw new IllegalArgumentException("За краем карты");
        }
        return pointerBoard.add(campaignId, x, y, fromX, fromY, who);
    }

    /**
     * M-22: подняться или спуститься по лестнице: фишка на клетке лестницы переходит на соседний уровень в ту же клетку.
     * Лестница стоит бега ({@link LevelMap#stairsCost}); мастер без ограничения не тратит.
     *
     * @param level куда; null — единственный, куда ведёт лестница
     * @return куда пришёл: «2 этаж»
     * @throws IllegalArgumentException если фишка не на лестнице, клетка наверху занята или бега не хватает
     */
    @Transactional
    public String climb(Long campaignId, Long participantId, Integer level, boolean free) {
        Encounter encounter = withMap(campaignId);
        EncounterParticipant participant = participant(encounter, participantId);
        if (participant.getPosX() == null) {
            throw new IllegalArgumentException("Фишка не на карте");
        }
        roundActions.requireTurn(participant);
        LevelMap levels = levels(encounter);
        List<Integer> to = levels.stairsFrom(participant.getPosLevel(), participant.getPosX(), participant.getPosY());
        if (to.isEmpty()) {
            throw new IllegalArgumentException("Здесь нет лестницы");
        }
        int target = level != null ? level : to.get(0);
        if (!to.contains(target)) {
            throw new IllegalArgumentException("Лестница не ведёт на этот уровень");
        }
        if (taken(encounter, participant, target, participant.getPosX(), participant.getPosY())) {
            throw new IllegalArgumentException("Наверху занято");
        }
        Integer run = free ? null : runOf(encounter, participant);
        if (run != null) {
            int cost = levels.stairsCost(participant.getPosLevel(), target);
            int left = Math.max(0, run - participant.getMovedMeters());
            if (cost > left) {
                throw new IllegalArgumentException("Лестница — " + cost + " м бега, осталось " + left + " из " + run + " м");
            }
            participant.setMovedMeters(participant.getMovedMeters() + cost);
        }
        participant.setPosLevel(target);
        return levels.doc().levelName(target);
    }

    /** Клетка уровня занята другой фишкой */
    private static boolean taken(Encounter encounter, EncounterParticipant participant, int level, int x, int y) {
        return encounter.getParticipants().stream()
                .anyMatch(other -> other != participant && other.getPosLevel() == level
                        && Integer.valueOf(x).equals(other.getPosX()) && Integer.valueOf(y).equals(other.getPosY()));
    }

    private LevelMap levels(Encounter encounter) {
        return new LevelMap(MapDoc.parse(encounter.getMapDoc()), rules(encounter));
    }

    private Encounter withMap(Long campaignId) {
        Encounter encounter = encounterRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Боя нет"));
        if (encounter.getMapDoc() == null) {
            throw new IllegalArgumentException("Бой идёт без карты");
        }
        return encounter;
    }

    /**
     * A-15: фишка — NPC мастера (в режиме «За NPC» мастер двигает только их)
     *
     * @throws NotFoundException если боя или участника нет
     */
    @Transactional(readOnly = true)
    public boolean isNpc(Long campaignId, Long participantId) {
        return participant(withMap(campaignId), participantId).getCharacter().isNpc();
    }

    private static EncounterParticipant participant(Encounter encounter, Long participantId) {
        return encounter.getParticipants().stream().filter(p -> p.getId().equals(participantId)).findFirst()
                .orElseThrow(NotFoundException.forId("EncounterParticipant", participantId));
    }

    private MapRules rules(Encounter encounter) {
        return systemRegistry.get(encounter.getCampaign().getSystem()).getMapRules();
    }

    private MapGrid grid(Encounter encounter) {
        return new MapGrid(MapDoc.parse(encounter.getMapDoc()), rules(encounter));
    }

    /** «Бустер 2» → «Б2», «Колясик» → «Ко» */
    static String initials(String name) {
        String[] words = Arrays.stream(name.strip().split("\\s+")).filter(word -> !word.isEmpty()).toArray(String[]::new);
        if (words.length == 0) {
            return "?";
        }
        if (words.length == 1) {
            return words[0].substring(0, Math.min(2, words[0].length()));
        }
        return Arrays.stream(words).limit(2).map(word -> word.substring(0, 1)).collect(Collectors.joining()).toUpperCase();
    }
}
