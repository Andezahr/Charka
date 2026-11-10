package app.charka.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * M-21: проверка карты на играбельность — числа вместо «на глаз». Что хорошо для боевой карты, собрано в
 * {@code docs/map-design.md}: несколько путей между сторонами без одного узкого места, укрытие на расстоянии
 * полудвижения, нет простреливаемых насквозь линий, стороны в нескольких движениях друг от друга, есть с чем
 * взаимодействовать. Двери считаются проходимыми (их открывают), окна — нет.
 * <p>
 * Пороги — наши ориентиры, а не правила игры: предупреждение значит «посмотрите», а не «ошибка». Улица может быть
 * простреливаемой нарочно.
 */
public final class MapAudit {

    /** Простреливаемая насквозь линия длиннее — предупреждение, м (дальность пистолета CP2020 — 50 м) */
    static final int LONG_SIGHT_METERS = 50;
    /** Узкое место — проход не шире стольких клеток (дверь, коридор); шире — уже фронт, а не бутылочное горлышко */
    static final int NARROW = 3;
    /** Когда система не знает, сколько пробегают за ход, м */
    static final int DEFAULT_MOVE_METERS = 10;

    private MapAudit() {
    }

    public enum Mark { OK, WARN, INFO }

    /** Строка проверки */
    public record Check(Mark mark, String title, String detail) {

        public String line() {
            return (mark == Mark.OK ? "✓ " : mark == Mark.WARN ? "⚠ " : "· ") + title + ": " + detail;
        }
    }

    /**
     * @param passages     сколько раздельных проходов в самом узком месте между зонами (0 — пути нет)
     * @param narrowest    сколько клеток в самом узком месте
     * @param pathCells    кратчайший путь от игроков до NPC, клеток
     * @param coverShare   доля клеток, где стоишь вплотную к укрытию, %
     * @param farFromCover самая дальняя клетка от укрытия, шагов
     * @param longestSight самая длинная простреливаемая прямая по ряду или столбцу, клеток
     * @param interactive  двери, окна, проёмы, местность и подписи
     */
    public record Report(List<Check> checks, int passages, int narrowest, int pathCells, int coverShare, int farFromCover,
                         int longestSight, int interactive) {

        public long warnings() {
            return checks.stream().filter(check -> check.mark() == Mark.WARN).count();
        }

        public String text() {
            StringBuilder out = new StringBuilder();
            checks.forEach(check -> out.append(check.line()).append('\n'));
            return out.toString();
        }
    }

    public static Report of(MapDoc doc, MapRules rules) {
        return new Auditor(doc, rules).run();
    }

    private static final class Auditor {

        private final MapDoc doc;
        private final MapRules rules;
        private final MapGrid grid;
        private final int w;
        private final int h;
        private final int moveCells;
        private final List<Check> checks = new ArrayList<>();
        private final boolean[] walk;
        private final boolean[] players;
        private final boolean[] npcs;

        Auditor(MapDoc doc, MapRules rules) {
            this.doc = doc;
            this.rules = rules;
            this.grid = new MapGrid(doc, rules);
            this.w = doc.w();
            this.h = doc.h();
            Integer move = rules.typicalMoveMeters();
            this.moveCells = Math.max(1, (move == null ? DEFAULT_MOVE_METERS : move) / doc.cell());
            walk = new boolean[w * h];
            players = new boolean[w * h];
            npcs = new boolean[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    walk[id(x, y)] = !grid.blocked(x, y);
                }
            }
            for (MapDoc.Zone zone : doc.zones()) {
                boolean[] target = MapDoc.PLAYERS.equals(zone.kind()) ? players : MapDoc.NPCS.equals(zone.kind()) ? npcs : null;
                for (int y = zone.y(); y < zone.y() + zone.h() && target != null; y++) {
                    for (int x = zone.x(); x < zone.x() + zone.w(); x++) {
                        if (doc.inside(x, y) && walk[id(x, y)]) {
                            target[id(x, y)] = true;
                        }
                    }
                }
            }
        }

        private int id(int x, int y) {
            return y * w + x;
        }

        private String at(int cell) {
            return (cell % w) + "," + (cell / w);
        }

        /** Шаг между соседями: дверь открывают, проём проходят, стену и окно — нет */
        private boolean step(int x1, int y1, int x2, int y2) {
            if (!doc.inside(x2, y2) || !walk[id(x2, y2)]) {
                return false;
            }
            MapDoc.Edge edge = doc.edge(MapDoc.between(x1, y1, x2, y2));
            return edge == null || edge.kind() == MapDoc.Edge.Kind.DOOR || edge.kind() == MapDoc.Edge.Kind.GAP;
        }

        private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        Report run() {
            checks.add(new Check(Mark.INFO, "Размер", w + "×" + h + " клеток, " + w * doc.cell() + "×" + h * doc.cell()
                    + " м; ход — около " + moveCells + " клеток"));
            int[] route = routes();
            int pathCells = distance();
            int[] cover = cover();
            int[] sight = sight();
            int interactive = interactive();
            return new Report(List.copyOf(checks), route[0], route[1], pathCells, cover[0], cover[1], sight[0], interactive);
        }

        /** Самое узкое место между зонами (минимальный разрез по клеткам) и на сколько раздельных проходов оно делится */
        private int[] routes() {
            if (!any(players) || !any(npcs)) {
                checks.add(new Check(Mark.WARN, "Зоны старта", "нужны обе: игроков (P) и NPC (N)"));
                return new int[]{0, 0};
            }
            int n = w * h;
            FlowGraph graph = new FlowGraph(2 * n + 2);
            int source = 2 * n;
            int sink = 2 * n + 1;
            int inf = n + 10;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = id(x, y);
                    if (!walk[c]) {
                        continue;
                    }
                    boolean zone = players[c] || npcs[c];
                    graph.add(2 * c, 2 * c + 1, zone ? inf : 1);
                    for (int[] d : SIDES) {
                        if (step(x, y, x + d[0], y + d[1])) {
                            graph.add(2 * c + 1, 2 * id(x + d[0], y + d[1]), inf);
                        }
                    }
                    if (players[c]) {
                        graph.add(source, 2 * c, inf);
                    }
                    if (npcs[c]) {
                        graph.add(2 * c + 1, sink, inf);
                    }
                }
            }
            int flow = graph.maxFlow(source, sink, 40);
            if (flow == 0) {
                checks.add(new Check(Mark.WARN, "Пути", "от игроков до NPC не дойти даже через двери"));
                return new int[]{0, 0};
            }
            // клетки разреза: вход достижим из источника, выход — нет; ищем с обеих сторон
            List<Integer> fromPlayers = cut(graph.reachable(source, false), n);
            List<Integer> fromNpcs = cut(invert(graph.reachable(sink, true)), n);
            List<Integer> worst = clusters(fromPlayers) <= clusters(fromNpcs) ? fromPlayers : fromNpcs;
            int passages = flow >= 40 ? 40 : clusters(worst);
            String where = flow >= 40 ? "открытая местность" : "у клеток " + worst.stream().limit(6).map(this::at).toList()
                    + (worst.size() > 6 ? "…" : "");
            if (passages <= 1 && worst.size() <= NARROW) {
                checks.add(new Check(Mark.WARN, "Пути", "всё проходит через одно узкое место шириной " + worst.size()
                        + " кл. (" + where + ") — добавьте обход: второй вход, чёрный ход, окно, проём"));
            } else if (passages <= 1) {
                checks.add(new Check(Mark.OK, "Пути", "в самом узком месте — один широкий фронт, " + worst.size() + " кл. (" + where + ")"));
            } else {
                checks.add(new Check(Mark.OK, "Пути", "в самом узком месте " + passages
                        + " раздельных прохода(ов), " + Math.min(flow, worst.size()) + " кл. (" + where + ")"));
            }
            return new int[]{passages, flow};
        }

        private boolean[] invert(boolean[] reachable) {
            boolean[] out = new boolean[reachable.length];
            for (int i = 0; i < reachable.length; i++) {
                out[i] = !reachable[i];
            }
            return out;
        }

        private List<Integer> cut(boolean[] sourceSide, int n) {
            List<Integer> cells = new ArrayList<>();
            for (int c = 0; c < n; c++) {
                if (walk[c] && sourceSide[2 * c] && !sourceSide[2 * c + 1]) {
                    cells.add(c);
                }
            }
            return cells;
        }

        /** Сколько групп клеток, между которыми можно шагнуть: двойная дверь — одна группа, две двери порознь — две */
        private int clusters(List<Integer> cells) {
            if (cells.isEmpty()) {
                return Integer.MAX_VALUE;
            }
            boolean[] in = new boolean[w * h];
            cells.forEach(c -> in[c] = true);
            boolean[] seen = new boolean[w * h];
            int groups = 0;
            for (int start : cells) {
                if (seen[start]) {
                    continue;
                }
                groups++;
                ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(start));
                seen[start] = true;
                while (!queue.isEmpty()) {
                    int c = queue.poll();
                    for (int[] d : SIDES) {
                        int x = c % w + d[0];
                        int y = c / w + d[1];
                        if (step(c % w, c / w, x, y) && in[id(x, y)] && !seen[id(x, y)]) {
                            seen[id(x, y)] = true;
                            queue.add(id(x, y));
                        }
                    }
                }
            }
            return groups;
        }

        private static boolean any(boolean[] cells) {
            for (boolean cell : cells) {
                if (cell) {
                    return true;
                }
            }
            return false;
        }

        /** Кратчайший путь от зоны игроков до зоны NPC в ходах */
        private int distance() {
            int[] dist = bfs(players);
            int best = Integer.MAX_VALUE;
            for (int c = 0; c < dist.length; c++) {
                if (npcs[c] && dist[c] >= 0) {
                    best = Math.min(best, dist[c]);
                }
            }
            if (best == Integer.MAX_VALUE) {
                return -1;
            }
            double moves = (double) best / moveCells;
            String text = best + " кл. (" + best * doc.cell() + " м), около " + String.format("%.1f", moves) + " хода";
            if (moves < 1.5) {
                checks.add(new Check(Mark.WARN, "Дистанция", text + " — стороны сходятся сразу, укрытия не успеют сыграть"));
            } else if (moves > 6) {
                checks.add(new Check(Mark.INFO, "Дистанция", text + " — долго сближаться: хорошо для снайпера и засады, иначе подвиньте зоны"));
            } else {
                checks.add(new Check(Mark.OK, "Дистанция", text));
            }
            return best;
        }

        private int[] bfs(boolean[] from) {
            int[] dist = new int[w * h];
            Arrays.fill(dist, -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int c = 0; c < from.length; c++) {
                if (from[c]) {
                    dist[c] = 0;
                    queue.add(c);
                }
            }
            while (!queue.isEmpty()) {
                int c = queue.poll();
                int x = c % w;
                int y = c / w;
                for (int[] d : SIDES) {
                    int nx = x + d[0];
                    int ny = y + d[1];
                    if (step(x, y, nx, ny) && dist[id(nx, ny)] < 0) {
                        dist[id(nx, ny)] = dist[c] + 1;
                        queue.add(id(nx, ny));
                    }
                }
            }
            return dist;
        }

        /** Клетка «у укрытия»: рядом по стороне стена, дверь или предмет с прочностью */
        private boolean covered(int x, int y) {
            for (int[] d : SIDES) {
                int nx = x + d[0];
                int ny = y + d[1];
                MapDoc.Edge edge = doc.edge(MapDoc.between(x, y, nx, ny));
                if (edge != null && (edge.kind() == MapDoc.Edge.Kind.WALL || edge.kind() == MapDoc.Edge.Kind.DOOR
                        || edge.kind() == MapDoc.Edge.Kind.WINDOW && rules.material(edge.material()).map(m -> m.sp() > 0).orElse(false))) {
                    return true;
                }
                // предмет, через который не пройти, — укрытие (у системы без прочности SP у всех 0)
                if (doc.inside(nx, ny) && grid.material(nx, ny).map(Material::blocksMove).orElse(false)) {
                    return true;
                }
            }
            return false;
        }

        private int[] cover() {
            boolean[] safe = new boolean[w * h];
            int walkable = 0;
            int near = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (walk[id(x, y)] && !water(x, y)) {
                        walkable++;
                        if (covered(x, y)) {
                            safe[id(x, y)] = true;
                            near++;
                        }
                    }
                }
            }
            if (walkable == 0) {
                return new int[]{0, 0};
            }
            if (near == 0) {
                checks.add(new Check(Mark.WARN, "Укрытия", "укрыться негде: ни стен, ни предметов, через которые не пройти"));
                return new int[]{0, Integer.MAX_VALUE};
            }
            int[] dist = bfs(safe);
            int far = 0;
            int farCell = -1;
            int open = 0;
            for (int c = 0; c < dist.length; c++) {
                if (!walk[c] || water(c % w, c / w)) {
                    continue;
                }
                if (dist[c] > far) {
                    far = dist[c];
                    farCell = c;
                }
                if (dist[c] > moveCells / 2) {
                    open++;
                }
            }
            int share = near * 100 / walkable;
            int half = Math.max(1, moveCells / 2);
            String text = share + "% клеток вплотную к укрытию; дальше всего от укрытия — " + far + " шаг(ов)"
                    + (farCell >= 0 ? " у клетки " + at(farCell) : "");
            if (far > half) {
                checks.add(new Check(Mark.WARN, "Укрытия", text + "; " + open + " кл. дальше полухода (" + half
                        + ") — голая зона простреливается: добавьте ящики, машины, колонны, если она не задумана"));
            } else {
                checks.add(new Check(Mark.OK, "Укрытия", text));
            }
            return new int[]{share, far};
        }

        /** Самая длинная прямая по ряду или столбцу, где ничто не закрывает взгляд */
        private int[] sight() {
            int best = 0;
            String where = "";
            for (int y = 0; y < h; y++) {
                int run = 0;
                for (int x = 0; x < w; x++) {
                    boolean open = clear(x, y) && (run == 0 || seeThrough(x - 1, y, x, y));
                    run = open ? run + 1 : clear(x, y) ? 1 : 0;
                    if (run > best) {
                        best = run;
                        where = "ряд " + y + ", до клетки " + x + "," + y;
                    }
                }
            }
            for (int x = 0; x < w; x++) {
                int run = 0;
                for (int y = 0; y < h; y++) {
                    boolean open = clear(x, y) && (run == 0 || seeThrough(x, y - 1, x, y));
                    run = open ? run + 1 : clear(x, y) ? 1 : 0;
                    if (run > best) {
                        best = run;
                        where = "столбец " + x + ", до клетки " + x + "," + y;
                    }
                }
            }
            int meters = best * doc.cell();
            String text = best + " кл. (" + meters + " м), " + where;
            checks.add(new Check(meters > LONG_SIGHT_METERS ? Mark.WARN : Mark.OK, "Прострел",
                    meters > LONG_SIGHT_METERS ? text + " — линия длиннее " + LONG_SIGHT_METERS
                            + " м: снайпер держит её целиком; перебейте высоким предметом, если не задумано" : text));
            return new int[]{best};
        }

        private boolean clear(int x, int y) {
            return walk[id(x, y)] && !water(x, y) && grid.material(x, y).map(m -> !m.blocksSight()).orElse(true);
        }

        /** По воде плывут, а не бегут от укрытия к укрытию: в укрытиях и прострелах она не считается */
        private boolean water(int x, int y) {
            return "water".equals(doc.cells().get(MapDoc.cellKey(x, y)));
        }

        private boolean seeThrough(int x1, int y1, int x2, int y2) {
            MapDoc.Edge edge = doc.edge(MapDoc.between(x1, y1, x2, y2));
            return edge == null || !edge.blocksSight();
        }

        /** С чем взаимодействовать: двери, окна, проёмы, особая местность, разные предметы, подписи */
        private int interactive() {
            Map<String, Integer> count = new TreeMap<>();
            doc.walls().values().forEach(value -> {
                MapDoc.Edge edge = MapDoc.Edge.of(value);
                String kind = switch (edge.kind()) {
                    case DOOR -> "двери";
                    case WINDOW -> "окна";
                    case GAP -> "проёмы";
                    default -> null;
                };
                if (kind != null) {
                    count.merge(kind, 1, Integer::sum);
                }
            });
            long terrain = doc.cells().values().stream().distinct().count();
            long objects = doc.stamps().stream().map(MapDoc.Stamp::code).distinct().count();
            long tall = doc.stamps().stream().filter(stamp -> rules.object(stamp.code())
                    .map(object -> object.cells().stream().anyMatch(code -> rules.material(code).map(Material::blocksSight).orElse(false)))
                    .orElse(false)).count();
            count.put("видов местности", (int) terrain);
            count.put("видов предметов", (int) objects);
            count.put("высоких предметов", (int) tall);
            count.put("подписей", doc.labels().size());
            int doorsAndWindows = count.getOrDefault("двери", 0) + count.getOrDefault("окна", 0) + count.getOrDefault("проёмы", 0);
            int score = doorsAndWindows + (int) terrain + (int) objects;
            String text = count.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).reduce((a, b) -> a + ", " + b).orElse("");
            if (doorsAndWindows == 0 && objects < 3) {
                checks.add(new Check(Mark.WARN, "Окружение", text + " — карта плоская: дайте двери, окна, разные укрытия"));
            } else if (objects < 3 || tall == 0) {
                checks.add(new Check(Mark.WARN, "Окружение", text + (tall == 0
                        ? " — нет высоких предметов: всё простреливается поверх" : " — мало разных предметов")));
            } else {
                checks.add(new Check(Mark.OK, "Окружение", text));
            }
            return score;
        }
    }

    /** Поток в сети с единичными пропускными способностями клеток (Эдмондс — Карп) */
    private static final class FlowGraph {

        private final int[] head;
        private int[] to = new int[1024];
        private int[] next = new int[1024];
        private int[] cap = new int[1024];
        private int edges;

        FlowGraph(int nodes) {
            head = new int[nodes];
            Arrays.fill(head, -1);
        }

        void add(int from, int target, int capacity) {
            link(from, target, capacity);
            link(target, from, 0);
        }

        private void link(int from, int target, int capacity) {
            if (edges == to.length) {
                to = Arrays.copyOf(to, edges * 2);
                next = Arrays.copyOf(next, edges * 2);
                cap = Arrays.copyOf(cap, edges * 2);
            }
            to[edges] = target;
            cap[edges] = capacity;
            next[edges] = head[from];
            head[from] = edges++;
        }

        /** Поток до {@code limit}: дальше считать незачем */
        int maxFlow(int source, int sink, int limit) {
            int flow = 0;
            int[] via = new int[head.length];
            while (flow < limit) {
                Arrays.fill(via, -1);
                ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(source));
                via[source] = -2;
                while (!queue.isEmpty() && via[sink] == -1) {
                    int node = queue.poll();
                    for (int e = head[node]; e >= 0; e = next[e]) {
                        if (cap[e] > 0 && via[to[e]] == -1) {
                            via[to[e]] = e;
                            queue.add(to[e]);
                        }
                    }
                }
                if (via[sink] == -1) {
                    break;
                }
                for (int node = sink; node != source; node = to[via[node] ^ 1]) {
                    cap[via[node]]--;
                    cap[via[node] ^ 1]++;
                }
                flow++;
            }
            return flow;
        }

        /**
         * Узлы, достижимые в остаточной сети от {@code start}; {@code backwards} — до {@code start} (обратные дуги).
         */
        boolean[] reachable(int start, boolean backwards) {
            boolean[] seen = new boolean[head.length];
            ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(start));
            seen[start] = true;
            while (!queue.isEmpty()) {
                int node = queue.poll();
                for (int e = head[node]; e >= 0; e = next[e]) {
                    // вперёд — по дуге с остатком; назад — по дуге, у которой остаток есть в сторону node
                    int residual = backwards ? cap[e ^ 1] : cap[e];
                    if (residual > 0 && !seen[to[e]]) {
                        seen[to[e]] = true;
                        queue.add(to[e]);
                    }
                }
            }
            return seen;
        }
    }
}
