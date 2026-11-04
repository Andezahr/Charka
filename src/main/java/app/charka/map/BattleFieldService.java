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
     */
    public record Token(Long id, Long character, String name, String initials, boolean npc, Integer x, Integer y,
                        boolean current, boolean done, boolean out, boolean alarming, String status, int moved, String actionOption) {
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
     */
    public record Field(Long mapId, String turn, MapDoc doc, String metric, List<Material> materials, List<MapObject> objects,
                        List<Token> tokens, List<PointerBoard.Pointer> pointers, String backgroundUrl, String fog) {
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
     * @param cells  клетки, видимые хоть одному его персонажу; null — тумана нет
     */
    private record Fog(Set<Long> hidden, String cells) {

        static final Fog NONE = new Fog(Set.of(), null);
    }

    /**
     * Что скрыто от игрока. Свои и персонажи других игроков видны всегда (HOMEBREW: команда знает, где свои),
     * NPC — только если его клетку видит хоть один персонаж игрока на карте хотя бы краем.
     */
    private Fog fog(Encounter encounter, MapGrid grid, Viewer viewer) {
        if (viewer.gm() || !encounter.getCampaign().isFogOfWar()) {
            return Fog.NONE;
        }
        boolean[] seen = new boolean[grid.doc().w() * grid.doc().h()];
        encounter.getParticipants().stream()
                .filter(p -> p.getPosX() != null && viewer.mine().contains(p.getCharacter().getId()))
                .forEach(eye -> {
                    boolean[] visible = grid.visibleFrom(eye.getPosX(), eye.getPosY());
                    for (int i = 0; i < seen.length; i++) {
                        seen[i] |= visible[i];
                    }
                });
        Set<Long> hidden = new HashSet<>();
        for (EncounterParticipant p : encounter.getParticipants()) {
            boolean ally = !p.getCharacter().isNpc() || viewer.mine().contains(p.getCharacter().getId());
            if (!ally && (p.getPosX() == null || !seen[p.getPosY() * grid.doc().w() + p.getPosX()])) {
                hidden.add(p.getId());
            }
        }
        StringBuilder cells = new StringBuilder(seen.length);
        for (boolean visible : seen) {
            cells.append(visible ? '1' : '0');
        }
        return new Fog(hidden, cells.toString());
    }

    /**
     * M-16: участники, которых игрок сейчас не видит; мастеру, без карты или без тумана — пусто.
     */
    @Transactional(readOnly = true)
    public Set<Long> hiddenFor(Long campaignId, Viewer viewer) {
        return encounterRepository.findByCampaignId(campaignId)
                .filter(encounter -> encounter.getMapDoc() != null)
                .map(encounter -> fog(encounter, grid(encounter), viewer).hidden())
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
        Fog fog = fog(encounter, grid(encounter), viewer);
        List<Token> tokens = view.get().rows().stream().filter(row -> !fog.hidden().contains(row.participant().getId())).map(row -> {
            EncounterParticipant participant = row.participant();
            return new Token(participant.getId(), row.character().getId(), row.character().getName(), initials(row.character().getName()),
                    row.character().isNpc(), participant.getPosX(), participant.getPosY(), row.current(), participant.isDone(),
                    row.card().out(), row.card().alarming(), row.card().status(), participant.getMovedMeters(), row.actionOption());
        }).toList();
        String turn = encounter.getRound() + ":" + (view.get().current() == null ? "" : view.get().current().getId());
        MapDoc doc = MapDoc.parse(encounter.getMapDoc());
        return Optional.of(new Field(encounter.getBattleMapId(), turn, doc, rules.metric(), rules.materials(), rules.objects(), tokens,
                pointerBoard.live(campaignId), EditableMap.backgroundUrl(campaignId, encounter.getBattleMapId(), doc), fog.cells()));
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
        Encounter encounter = withMap(campaignId);
        EncounterParticipant participant = participant(encounter, participantId);
        MapGrid grid = grid(encounter);
        if (!grid.doc().inside(x, y)) {
            throw new IllegalArgumentException("За краем карты");
        }
        if (grid.blocked(x, y)) {
            throw new IllegalArgumentException("Туда не встать: " + grid.material(x, y).map(Material::label).orElse("занято"));
        }
        boolean taken = encounter.getParticipants().stream()
                .anyMatch(other -> other != participant && Integer.valueOf(x).equals(other.getPosX()) && Integer.valueOf(y).equals(other.getPosY()));
        if (taken) {
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
        Encounter encounter = withMap(campaignId);
        MapGrid grid = grid(encounter);
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
        boolean taken = encounter.getParticipants().stream()
                .anyMatch(other -> other != participant && Integer.valueOf(x).equals(other.getPosX()) && Integer.valueOf(y).equals(other.getPosY()));
        if (taken) {
            throw new IllegalArgumentException("Клетка занята");
        }
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
        return new Reach(left, run, grid(encounter).reach(participant.getPosX(), participant.getPosY(), left));
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
        MapGrid grid = grid(encounter);
        if (fog(encounter, grid, viewer).hidden().contains(to.getId())) {
            throw new IllegalArgumentException("Цель не видна");
        }
        return sight(encounter, grid, rules(encounter), from, to);
    }

    private Sight sight(Encounter encounter, MapGrid grid, MapRules rules, EncounterParticipant from, EncounterParticipant to) {
        int meters = rules.distance(grid.doc().cell(), from.getPosX(), from.getPosY(), to.getPosX(), to.getPosY());
        MapGrid.Sight sight = grid.sight(from.getPosX(), from.getPosY(), to.getPosX(), to.getPosY(), COVER_NEAR_CELLS);
        boolean hidden = sight.visibility() == MapGrid.Visibility.NONE;
        // вслепую — только если система знает такой модификатор
        Optional<Material> through = rules.blindFireOption() == null ? Optional.empty() : sight.through();
        Optional<Material> cover = hidden ? through : sight.cover();
        List<String> inTheWay = encounter.getParticipants().stream()
                .filter(p -> p != from && p != to && p.getPosX() != null)
                .filter(p -> sight.path().stream().anyMatch(cell -> cell[0] == p.getPosX() && cell[1] == p.getPosY()))
                .map(p -> p.getCharacter().getName()).toList();
        return new Sight(meters, hidden, cover.map(Material::code).orElse(null), cover.map(Material::label).orElse(null),
                sight.visibility().name().toLowerCase(), sight.exposed(),
                through.map(material -> material.label() + (material.sp() > 0 ? " SP " + material.sp() : "")).orElse(null),
                through.isPresent() ? rules.blindFireOption() : null, inTheWay);
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
        MapGrid grid = grid(encounter);
        Set<Long> hidden = fog(encounter, grid, viewer).hidden();
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
                    Sight sight = sight(encounter, grid, rules, from, p);
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
        Encounter encounter = withMap(campaignId);
        MapDoc doc = MapDoc.parse(encounter.getMapDoc());
        MapDoc.Edge edge = doc.edge(edgeKey);
        if (edge == null || edge.kind() != MapDoc.Edge.Kind.DOOR) {
            throw new IllegalArgumentException("Здесь нет двери");
        }
        doc.walls().put(edgeKey, new MapDoc.Edge(edge.kind(), edge.material(), !edge.open()).toString());
        encounter.setMapDoc(doc.toJson());
    }

    /**
     * Мастер правит копию карты боя (M-11): взорвали стену, сдвинули ящик.
     */
    @Transactional
    public void updateDoc(Long campaignId, String json) {
        Encounter encounter = withMap(campaignId);
        MapDoc doc = MapDoc.parse(json);
        encounter.setMapDoc(doc.toJson());
        // фишки за новым краем или в новом предмете уходят в лоток
        MapGrid grid = grid(encounter);
        encounter.getParticipants().stream()
                .filter(p -> p.getPosX() != null && grid.blocked(p.getPosX(), p.getPosY()))
                .forEach(p -> {
                    p.setPosX(null);
                    p.setPosY(null);
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

    private Encounter withMap(Long campaignId) {
        Encounter encounter = encounterRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Боя нет"));
        if (encounter.getMapDoc() == null) {
            throw new IllegalArgumentException("Бой идёт без карты");
        }
        return encounter;
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
