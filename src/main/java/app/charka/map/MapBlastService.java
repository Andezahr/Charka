package app.charka.map;

import app.charka.attack.MapBlast;
import app.charka.dice.DiceExpression;
import app.charka.model.Character;
import app.charka.model.Encounter;
import app.charka.model.EncounterParticipant;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.repository.EncounterRepository;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * M-23: взрыв на карте идущего боя. Меняет копию карты боя, заготовку не трогает.
 * <p>
 * HOMEBREW: в книге нет разрушения укрытий. Взрыв сносит грань (стену, дверь, окно) и предмет, если его урон больше
 * удвоенного SP: граната 7D6 выносит дверь и гипсокартон, но не кирпич. От предмета остаются обломки, грань становится проёмом.
 * Взрывоопасное в радиусе рвётся следом, цепочкой.
 */
@Component
@RequiredArgsConstructor
public class MapBlastService implements MapBlast {

    /** Сколько SP выносит урон: больше {@code SP × BREAK_FACTOR} */
    static final int BREAK_FACTOR = 2;

    private final EncounterRepository encounterRepository;
    private final GameSystemRegistry systemRegistry;

    @Override
    @Transactional
    public Result explodeAt(Character center, int amount, int radius, DiceRoller dice) {
        if (center == null || center.getCampaign() == null) {
            return Result.NONE;
        }
        Optional<Encounter> encounter = withMap(center.getCampaign().getId());
        if (encounter.isEmpty()) {
            return Result.NONE;
        }
        Optional<EncounterParticipant> placed = encounter.get().getParticipants().stream()
                .filter(p -> p.getCharacter().getId().equals(center.getId()) && p.getPosX() != null).findFirst();
        if (placed.isEmpty()) {
            return Result.NONE;
        }
        MapRules rules = rules(encounter.get());
        MapDoc doc = MapDoc.parse(encounter.get().getMapDoc());
        List<String> steps = new ArrayList<>();
        Deque<MapDoc.Stamp> queue = new ArrayDeque<>();
        wreck(doc, rules, new Blast(null, placed.get().getPosX(), placed.get().getPosY(), amount, radius), steps, queue);
        return chain(encounter.get(), doc, rules, queue, steps, dice);
    }

    @Override
    @Transactional
    public Result detonate(Long campaignId, int x, int y, DiceRoller dice) {
        Encounter encounter = withMap(campaignId).orElseThrow(() -> new IllegalArgumentException("Боя на карте нет"));
        MapRules rules = rules(encounter);
        MapDoc doc = MapDoc.parse(encounter.getMapDoc());
        MapDoc.Stamp stamp = stampAt(doc, rules, x, y)
                .filter(found -> explosiveOf(rules, found).isPresent())
                .orElseThrow(() -> new IllegalArgumentException("Здесь нечему взрываться"));
        Deque<MapDoc.Stamp> queue = new ArrayDeque<>(List.of(stamp));
        return chain(encounter, doc, rules, queue, new ArrayList<>(), dice);
    }

    /** Подорвать по очереди всё взрывоопасное, что задело; каждый взрыв ломает своё и может задеть следующее */
    private Result chain(Encounter encounter, MapDoc doc, MapRules rules, Deque<MapDoc.Stamp> queue, List<String> steps, DiceRoller dice) {
        List<Detonation> detonations = new ArrayList<>();
        Set<MapDoc.Stamp> done = new HashSet<>();
        while (!queue.isEmpty()) {
            MapDoc.Stamp stamp = queue.poll();
            if (!done.add(stamp)) {
                continue;
            }
            MapRules.Explosive explosive = explosiveOf(rules, stamp).orElseThrow();
            doc.stamps().remove(stamp);
            debris(doc, rules, stamp);
            DiceExpression expression = DiceExpression.parse(explosive.damage());
            List<Integer> rolls = new ArrayList<>();
            for (int i = 0; i < expression.count(); i++) {
                rolls.add(dice.roll(expression.sides()));
            }
            int amount = rolls.stream().mapToInt(Integer::intValue).sum() + expression.modifier();
            List<String> own = new ArrayList<>();
            own.add(explosive.label() + ": взрыв " + explosive.damage() + " " + rolls + " = " + amount + ", радиус " + explosive.radius()
                    + " м (хоумбрю)");
            Blast blast = new Blast(stamp, stamp.x(), stamp.y(), amount, explosive.radius());
            wreck(doc, rules, blast, own, queue);
            detonations.add(new Detonation(explosive.label(), explosive.damage(), amount, explosive.radius(), own,
                    caught(encounter, doc, rules, blast)));
        }
        encounter.setMapDoc(doc.toJson());
        return new Result(steps, detonations);
    }

    /** Сносит слабое в радиусе; взрывоопасное ставит в очередь */
    private void wreck(MapDoc doc, MapRules rules, Blast blast, List<String> steps, Deque<MapDoc.Stamp> queue) {
        int cell = doc.cell();
        List<String> broken = new ArrayList<>();
        Iterator<Map.Entry<String, String>> edges = doc.walls().entrySet().iterator();
        while (edges.hasNext()) {
            Map.Entry<String, String> entry = edges.next();
            MapDoc.Edge edge = MapDoc.Edge.of(entry.getValue());
            if (edge.kind() == MapDoc.Edge.Kind.GAP || !near(entry.getKey(), blast, cell)) {
                continue;
            }
            int sp = edge.material() == null ? 0 : rules.material(edge.material()).map(Material::sp).orElse(0);
            if (blast.amount() > sp * BREAK_FACTOR) {
                entry.setValue(new MapDoc.Edge(MapDoc.Edge.Kind.GAP, null, true).toString());
                broken.add(edgeName(edge, rules));
            }
        }
        for (MapDoc.Stamp stamp : new ArrayList<>(doc.stamps())) {
            if (stamp.equals(blast.source()) || !touches(stamp, rules, blast, cell)) {
                continue;
            }
            if (explosiveOf(rules, stamp).isPresent()) {
                queue.add(stamp);
                continue;
            }
            if (blast.amount() > minSp(stamp, rules) * BREAK_FACTOR) {
                doc.stamps().remove(stamp);
                debris(doc, rules, stamp);
                broken.add(rules.object(stamp.code()).map(MapObject::label).orElse(stamp.code()));
            }
        }
        if (!broken.isEmpty()) {
            steps.add("Взрыв снёс: " + String.join(", ", broken) + " (хоумбрю: урон больше SP × " + BREAK_FACTOR + ")");
        }
    }

    /** Кто в радиусе и не за целой стеной или закрытой дверью; предметы взрыв огибает */
    private static List<Character> caught(Encounter encounter, MapDoc doc, MapRules rules, Blast blast) {
        MapGrid grid = new MapGrid(doc, rules);
        return encounter.getParticipants().stream()
                .filter(p -> p.getPosX() != null)
                .filter(p -> rules.distance(doc.cell(), blast.x(), blast.y(), p.getPosX(), p.getPosY()) <= blast.radius())
                .filter(p -> grid.line(blast.x(), blast.y(), p.getPosX(), p.getPosY()).obstacles().stream()
                        .noneMatch(o -> o.material() != null
                                && (o.material().layer() == Material.Layer.WALL || o.material().layer() == Material.Layer.DOOR)))
                .map(EncounterParticipant::getCharacter)
                .toList();
    }

    private static boolean near(String edgeKey, Blast blast, int cell) {
        String[] parts = edgeKey.split(",");
        boolean top = "h".equals(parts[2]);
        double ex = Integer.parseInt(parts[0]) + (top ? 0.5 : 0);
        double ey = Integer.parseInt(parts[1]) + (top ? 0 : 0.5);
        return Math.hypot(ex - (blast.x() + 0.5), ey - (blast.y() + 0.5)) * cell <= blast.radius() + cell / 2.0;
    }

    private static boolean touches(MapDoc.Stamp stamp, MapRules rules, Blast blast, int cell) {
        Optional<MapObject> object = rules.object(stamp.code());
        if (object.isEmpty()) {
            return false;
        }
        for (int dy = 0; dy < object.get().height(stamp.rot()); dy++) {
            for (int dx = 0; dx < object.get().width(stamp.rot()); dx++) {
                if (Math.hypot(stamp.x() + dx - blast.x(), stamp.y() + dy - blast.y()) * cell <= blast.radius()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int minSp(MapDoc.Stamp stamp, MapRules rules) {
        return rules.object(stamp.code()).map(object -> object.cells().stream()
                .filter(Objects::nonNull)
                .mapToInt(code -> rules.material(code).map(Material::sp).orElse(0))
                .min().orElse(0)).orElse(0);
    }

    private static Optional<MapRules.Explosive> explosiveOf(MapRules rules, MapDoc.Stamp stamp) {
        return rules.object(stamp.code()).flatMap(object -> object.cells().stream()
                .filter(Objects::nonNull)
                .map(rules::explosive).flatMap(Optional::stream).findFirst());
    }

    /** Предмет, который занимает клетку */
    static Optional<MapDoc.Stamp> stampAt(MapDoc doc, MapRules rules, int x, int y) {
        return doc.stamps().stream().filter(stamp -> rules.object(stamp.code()).map(object ->
                x >= stamp.x() && y >= stamp.y() && x < stamp.x() + object.width(stamp.rot()) && y < stamp.y() + object.height(stamp.rot()))
                .orElse(false)).findFirst();
    }

    /** На месте предмета — обломки, если система их знает */
    private static void debris(MapDoc doc, MapRules rules, MapDoc.Stamp stamp) {
        if (rules.material("debris").isEmpty()) {
            return;
        }
        rules.object(stamp.code()).ifPresent(object -> {
            for (int dy = 0; dy < object.height(stamp.rot()); dy++) {
                for (int dx = 0; dx < object.width(stamp.rot()); dx++) {
                    if (doc.inside(stamp.x() + dx, stamp.y() + dy)) {
                        doc.cells().put(MapDoc.cellKey(stamp.x() + dx, stamp.y() + dy), "debris");
                    }
                }
            }
        });
    }

    private static String edgeName(MapDoc.Edge edge, MapRules rules) {
        String what = switch (edge.kind()) {
            case DOOR -> "дверь";
            case WINDOW -> "окно";
            default -> "стена";
        };
        return edge.material() == null ? what : what + " (" + rules.material(edge.material()).map(Material::label).orElse(edge.material()) + ")";
    }

    private Optional<Encounter> withMap(Long campaignId) {
        return encounterRepository.findByCampaignId(campaignId).filter(encounter -> encounter.getMapDoc() != null);
    }

    private MapRules rules(Encounter encounter) {
        return systemRegistry.get(encounter.getCampaign().getSystem()).getMapRules();
    }

    /**
     * @param source подорванный предмет (себя не ломает); null — граната
     */
    private record Blast(MapDoc.Stamp source, int x, int y, int amount, int radius) {
    }
}
