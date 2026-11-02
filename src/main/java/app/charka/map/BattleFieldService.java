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
import java.util.List;
import java.util.Optional;
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
     */
    public record Token(Long id, Long character, String name, String initials, boolean npc, Integer x, Integer y,
                        boolean current, boolean done, boolean out, boolean alarming, String status) {
    }

    /**
     * Всё, что рисует браузер. Меняется только от действий за столом: на этом держится ETag опроса.
     *
     * @param turn      раунд и чей ход: сменился — строки трекера устарели
     * @param doc       документ карты
     * @param metric    мера расстояния ({@link MapRules#metric})
     * @param materials материалы системы
     * @param objects   готовые предметы системы
     */
    public record Field(Long mapId, String turn, MapDoc doc, String metric, List<Material> materials, List<MapObject> objects,
                        List<Token> tokens, List<PointerBoard.Pointer> pointers, String backgroundUrl) {
    }

    /**
     * @return пусто — боя нет или он без карты
     */
    @Transactional(readOnly = true)
    public Optional<Field> field(Long campaignId) {
        Optional<EncounterView> view = encounterService.view(campaignId);
        if (view.isEmpty() || view.get().encounter().getMapDoc() == null) {
            return Optional.empty();
        }
        Encounter encounter = view.get().encounter();
        MapRules rules = rules(encounter);
        List<Token> tokens = view.get().rows().stream().map(row -> {
            EncounterParticipant participant = row.participant();
            return new Token(participant.getId(), row.character().getId(), row.character().getName(), initials(row.character().getName()),
                    row.character().isNpc(), participant.getPosX(), participant.getPosY(), row.current(), participant.isDone(),
                    row.card().out(), row.card().alarming(), row.card().status());
        }).toList();
        String turn = encounter.getRound() + ":" + (view.get().current() == null ? "" : view.get().current().getId());
        MapDoc doc = MapDoc.parse(encounter.getMapDoc());
        return Optional.of(new Field(encounter.getBattleMapId(), turn, doc, rules.metric(), rules.materials(), rules.objects(), tokens,
                pointerBoard.live(campaignId), EditableMap.backgroundUrl(campaignId, encounter.getBattleMapId(), doc)));
    }

    /**
     * Перенести фишку. Стены на пути не проверяются: решает мастер, досягаемость только подсказка.
     *
     * @throws IllegalArgumentException если клетка за краем, в предмете или занята
     */
    @Transactional
    public void move(Long campaignId, Long participantId, int x, int y) {
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
        participant.setPosX(x);
        participant.setPosY(y);
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
     * Куда участник добежит за действие (CP2020: бег MA × 3 м), не сквозь стены и закрытые двери.
     *
     * @param meters дистанция одного действия; null — система не знает, область не рисуется
     */
    public record Reach(Integer meters, List<int[]> cells) {
    }

    @Transactional(readOnly = true)
    public Reach reach(Long campaignId, Long participantId) {
        Encounter encounter = withMap(campaignId);
        EncounterParticipant participant = participant(encounter, participantId);
        GameSystem system = systemRegistry.get(encounter.getCampaign().getSystem());
        Integer meters = system.getMapRules().reach(system.getCurrentStats(participant.getCharacter()));
        if (meters == null || participant.getPosX() == null) {
            return new Reach(meters, List.of());
        }
        return new Reach(meters, grid(encounter).reach(participant.getPosX(), participant.getPosY(), meters));
    }

    /**
     * Что между двумя фишками: для диалога атаки.
     *
     * @param meters     расстояние по мере системы
     * @param blocked    нет линии видимости
     * @param cover      код укрытия цели (вариант модификатора атаки); null — не за укрытием
     * @param coverLabel название укрытия
     */
    public record Sight(int meters, boolean blocked, String cover, String coverLabel) {
    }

    /** HOMEBREW: укрытие считается, если препятствие не дальше клетки от цели — в книге способа нет */
    static final int COVER_NEAR_CELLS = 1;

    @Transactional(readOnly = true)
    public Sight sight(Long campaignId, Long fromParticipant, Long toParticipant) {
        Encounter encounter = withMap(campaignId);
        EncounterParticipant from = participant(encounter, fromParticipant);
        EncounterParticipant to = participant(encounter, toParticipant);
        if (from.getPosX() == null || to.getPosX() == null) {
            throw new IllegalArgumentException("Фишка не на карте");
        }
        MapGrid grid = grid(encounter);
        MapRules rules = rules(encounter);
        int meters = rules.distance(grid.doc().cell(), from.getPosX(), from.getPosY(), to.getPosX(), to.getPosY());
        MapGrid.Line line = grid.line(from.getPosX(), from.getPosY(), to.getPosX(), to.getPosY());
        Optional<MapGrid.Obstacle> cover = line.coverNear(COVER_NEAR_CELLS);
        return new Sight(meters, line.blocked(), cover.map(o -> o.material().code()).orElse(null), cover.map(o -> o.material().label()).orElse(null));
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
