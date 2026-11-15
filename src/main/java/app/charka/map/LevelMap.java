package app.charka.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * M-22: геометрия многоуровневой карты — плоская {@link MapGrid} на каждый уровень и то, что их связывает:
 * лестницы (клетка {@link #STAIRS} на соседних уровнях в одном месте), пустота {@link #VOID} (двор под балконом, проём
 * в полу) и взгляд между уровнями сквозь неё. Строится на один запрос.
 */
public final class LevelMap {

    /** Код материала: пустота — здесь нет пола, внизу видно нижний уровень; встать нельзя */
    public static final String VOID = "void";
    /** Код материала: лестница — на соседнем уровне в той же клетке тоже лестница, туда можно подняться или спуститься */
    public static final String STAIRS = "stairs";
    /**
     * ML-03: код материала: ступени марша — подъём к площадке {@link #STAIRS}. Бег по ним обычный: высоту оплачивает длина
     * марша, а шаг с площадки на другой уровень бесплатный (HOMEBREW: марш из 3 ступеней — 6 м, как стремянка
     * {@link #STAIRS_FACTOR} × 3 м). На верхнем уровне над маршем — пустота: лестничный проём.
     */
    public static final String STEPS = "steps";

    /**
     * HOMEBREW: подъём или спуск по лестнице стоит вдвое больше высоты в метрах бега: 3 м этажа — 6 м бега.
     * В книге нет; FNFF меряет только бег по ровному.
     */
    public static final int STAIRS_FACTOR = 2;

    private final MapDoc doc;
    private final MapRules rules;
    private final List<MapGrid> grids = new ArrayList<>();

    public LevelMap(MapDoc doc, MapRules rules) {
        this.doc = doc;
        this.rules = rules;
        for (int level = 0; level < doc.levelCount(); level++) {
            grids.add(new MapGrid(doc.level(level), rules));
        }
    }

    public MapDoc doc() {
        return doc;
    }

    /**
     * @throws IllegalArgumentException если уровня нет
     */
    public MapGrid grid(int level) {
        if (!doc.hasLevel(level)) {
            throw new IllegalArgumentException("Нет такого уровня: " + level);
        }
        return grids.get(level);
    }

    public int levels() {
        return grids.size();
    }

    private boolean is(int level, int x, int y, String code) {
        return code.equals(doc.level(level).cells().get(MapDoc.cellKey(x, y)));
    }

    /** Над клеткой нижнего уровня открыто до верхнего: на всех уровнях между ними в этой клетке пустота */
    private boolean openAbove(int lower, int upper, int x, int y) {
        for (int level = lower + 1; level <= upper; level++) {
            if (!is(level, x, y, VOID)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Куда ведёт лестница из клетки: уровни выше и ниже, где в той же клетке тоже лестница.
     */
    public List<Integer> stairsFrom(int level, int x, int y) {
        List<Integer> to = new ArrayList<>();
        if (!is(level, x, y, STAIRS)) {
            return to;
        }
        if (level + 1 < levels() && is(level + 1, x, y, STAIRS)) {
            to.add(level + 1);
        }
        if (level > 0 && is(level - 1, x, y, STAIRS)) {
            to.add(level - 1);
        }
        return to;
    }

    /**
     * ML-03: куда фишка добежит за {@code meters} м по всем уровням: по своему уровню, через площадку лестницы
     * ({@link #stairsFrom}) — на соседний, с ценой подъёма {@link #stairsCost}, и дальше по нему.
     *
     * @return клетки {@code {x, y, метров пути, уровень}}, по одной на клетку уровня — с самым коротким путём
     */
    public List<int[]> reach(int level, int x, int y, int meters) {
        java.util.Map<String, int[]> best = new java.util.LinkedHashMap<>();
        java.util.ArrayDeque<int[]> entries = new java.util.ArrayDeque<>();
        entries.add(new int[]{level, x, y, 0});
        while (!entries.isEmpty()) {
            int[] entry = entries.poll();
            int l = entry[0];
            for (int[] cell : grid(l).reach(entry[1], entry[2], meters - entry[3])) {
                int cost = entry[3] + cell[2];
                String key = l + ":" + cell[0] + "," + cell[1];
                int[] known = best.get(key);
                if (known != null && known[2] <= cost) {
                    continue;
                }
                best.put(key, new int[]{cell[0], cell[1], cost, l});
                for (int to : stairsFrom(l, cell[0], cell[1])) {
                    int up = cost + stairsCost(l, to, cell[0], cell[1]);
                    int[] there = best.get(to + ":" + cell[0] + "," + cell[1]);
                    if (up <= meters && (there == null || there[2] > up) && !grid(to).blocked(cell[0], cell[1])) {
                        entries.add(new int[]{to, cell[0], cell[1], up});
                    }
                }
            }
        }
        return new ArrayList<>(best.values());
    }

    /**
     * ML-03: сколько стоит перейти с площадки в клетке {@code (x, y)}: у марша (рядом ступени {@link #STEPS} на нижнем
     * уровне) — 0, высоту уже оплатили ступени; иначе стремянка или люк — {@link #stairsCost(int, int)}.
     */
    public int stairsCost(int from, int to, int x, int y) {
        int lower = Math.min(from, to);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && is(lower, x + dx, y + dy, STEPS)) {
                    return 0;
                }
            }
        }
        return stairsCost(from, to);
    }

    /** Сколько метров бега стоит стремянка между уровнями: высота × {@link #STAIRS_FACTOR} */
    public int stairsCost(int from, int to) {
        return Math.abs(doc.levelZ(to) - doc.levelZ(from)) * STAIRS_FACTOR;
    }

    /** Расстояние, м: по плоскости мерой системы, между уровнями — с высотой по прямой */
    public int distance(int l1, int x1, int y1, int l2, int x2, int y2) {
        int flat = rules.distance(doc.cell(), x1, y1, x2, y2);
        if (l1 == l2) {
            return flat;
        }
        double plane = Math.hypot(x2 - x1, y2 - y1) * doc.cell();
        return (int) Math.round(Math.hypot(plane, doc.levelZ(l2) - doc.levelZ(l1)));
    }

    /**
     * Видимость цели. На одном уровне — как {@link MapGrid#sight}. Между уровнями — только сквозь пустоту: сверху видно
     * клетку, над которой открыто до уровня смотрящего, если на его уровне линия до неё свободна (перила и парапет
     * закрывают); снизу — так же, если открыто над смотрящим. Стрелку выше цели укрытие цели не мешает (с. 103:
     * «cover doesn't count jack if the guy shooting at you is higher than you are»).
     *
     * @return пусто — уровни не видят друг друга в этих клетках
     */
    public Optional<MapGrid.Sight> sight(int l1, int fx, int fy, int l2, int tx, int ty, int near) {
        if (l1 == l2) {
            return Optional.of(grid(l1).sight(fx, fy, tx, ty, near));
        }
        if (l1 > l2) {
            if (!openAbove(l2, l1, tx, ty)) {
                return Optional.empty();
            }
            MapGrid.Sight upper = grid(l1).sight(fx, fy, tx, ty, near);
            // стрелок выше: укрытие цели не в счёт
            return Optional.of(new MapGrid.Sight(upper.visibility(), upper.exposed(), Optional.empty(), upper.through(), List.of(), upper.smoke()));
        }
        if (!openAbove(l1, l2, fx, fy)) {
            return Optional.empty();
        }
        MapGrid.Sight upper = grid(l2).sight(fx, fy, tx, ty, near);
        return Optional.of(new MapGrid.Sight(upper.visibility(), upper.exposed(), upper.cover(), upper.through(), List.of(), upper.smoke()));
    }

    /**
     * Поле видимости смотрящего по уровням: на своём — {@link MapGrid#visibleFrom}; ниже — клетки под видимой пустотой.
     *
     * @return по уровню массив {@code w × h}
     */
    public boolean[][] visibleFrom(int level, int fx, int fy) {
        boolean[][] seen = new boolean[levels()][];
        int w = doc.w();
        for (int l = 0; l < levels(); l++) {
            seen[l] = new boolean[w * doc.h()];
        }
        seen[level] = grid(level).visibleFrom(fx, fy);
        for (int i = 0; i < seen[level].length; i++) {
            if (!seen[level][i]) {
                continue;
            }
            int x = i % w;
            int y = i / w;
            // вниз по пустоте, пока пол не закроет
            for (int below = level - 1; below >= 0 && is(below + 1, x, y, VOID); below--) {
                seen[below][i] = true;
            }
        }
        return seen;
    }
}
