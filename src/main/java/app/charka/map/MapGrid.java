package app.charka.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Геометрия карты для правил: что стоит в клетке, можно ли пройти и увидеть, куда добежать.
 * Строится из документа и правил системы на один запрос; документ не меняет.
 */
public final class MapGrid {

    private final MapDoc doc;
    private final MapRules rules;
    /** Материал клетки: предмет поверх местности */
    private final Map<Long, String> cellMaterials = new HashMap<>();

    public MapGrid(MapDoc doc, MapRules rules) {
        this.doc = doc;
        this.rules = rules;
        doc.cells().forEach((key, material) -> {
            String[] xy = key.split(",");
            cellMaterials.put(pack(Integer.parseInt(xy[0]), Integer.parseInt(xy[1])), material);
        });
        for (MapDoc.Stamp stamp : doc.stamps()) {
            rules.object(stamp.code()).ifPresent(object -> {
                for (int dy = 0; dy < object.height(stamp.rot()); dy++) {
                    for (int dx = 0; dx < object.width(stamp.rot()); dx++) {
                        String material = object.cellAt(stamp.rot(), dx, dy);
                        if (material != null && doc.inside(stamp.x() + dx, stamp.y() + dy)) {
                            cellMaterials.put(pack(stamp.x() + dx, stamp.y() + dy), material);
                        }
                    }
                }
            });
        }
    }

    public MapDoc doc() {
        return doc;
    }

    private static long pack(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /** Материал клетки; пусто — пол */
    public Optional<Material> material(int x, int y) {
        String code = cellMaterials.get(pack(x, y));
        return code == null ? Optional.empty() : rules.material(code);
    }

    /** На клетку нельзя встать: за краем или в ней предмет, через который не пройти */
    public boolean blocked(int x, int y) {
        return !doc.inside(x, y) || material(x, y).map(Material::blocksMove).orElse(false);
    }

    /** Между соседними по стороне клетками не пройти: стена, окно, закрытая дверь */
    public boolean wallBetween(int x1, int y1, int x2, int y2) {
        MapDoc.Edge edge = doc.edge(MapDoc.between(x1, y1, x2, y2));
        return edge != null && edge.blocksMove();
    }

    /**
     * Что на линии между центрами клеток. Угол двух граней пропускает взгляд, если открыт хотя бы один обход.
     *
     * @param blocked   линию закрывает стена, закрытая дверь или непрозрачная клетка
     * @param obstacles что пересекает линия: материал и в скольких клетках от цели
     */
    public record Line(boolean blocked, List<Obstacle> obstacles) {

        /**
         * Укрытие цели: ближайшее к цели препятствие с прочностью не дальше {@code near} клеток.
         */
        public Optional<Obstacle> coverNear(int near) {
            Obstacle best = null;
            for (Obstacle obstacle : obstacles) {
                if (obstacle.material() != null && obstacle.material().sp() > 0 && obstacle.cellsFromTarget() <= near
                        && (best == null || obstacle.cellsFromTarget() <= best.cellsFromTarget())) {
                    best = obstacle;
                }
            }
            return Optional.ofNullable(best);
        }
    }

    /**
     * @param material        из чего; null — грань без известного материала
     * @param cellsFromTarget расстояние до цели в клетках по большей оси
     */
    public record Obstacle(Material material, int cellsFromTarget) {
    }

    /** Линия между центрами клеток */
    public Line line(int x1, int y1, int x2, int y2) {
        Ray ray = trace(x1 + 0.5, y1 + 0.5, x2 + 0.5, y2 + 0.5, x1, y1, x2, y2, false);
        List<Obstacle> all = new ArrayList<>(ray.blockers());
        all.addAll(ray.obstacles());
        all.sort(Comparator.comparingInt(Obstacle::cellsFromTarget).reversed());
        return new Line(ray.blocked(), all);
    }

    /** Насколько видна цель: {@link #SAMPLES} точек её клетки из стольких же точек клетки стрелка */
    public enum Visibility { FULL, PARTIAL, NONE }

    /**
     * Что между стрелком и целью для правил атаки.
     *
     * @param visibility видна целиком, частично (выглядывает из-за угла, стоит в проёме) или не видна
     * @param exposed    сколько из {@link #SAMPLES} точек клетки цели видно
     * @param cover      укрытие цели: самое прочное из того, что её частично закрывает, и низких предметов рядом с ней
     * @param through    цель не видна, но между центрами ровно одна преграда с прочностью: сквозь неё стреляют вслепую
     * @param path       клетки на линии между центрами без стрелка и цели: кто в них стоит — на линии огня
     */
    public record Sight(Visibility visibility, int exposed, Optional<Material> cover, Optional<Material> through, List<int[]> path) {
    }

    /** Точек в клетке: центр и четыре у углов */
    public static final int SAMPLES = 5;

    /**
     * HOMEBREW: точки у углов — в 0,4 клетки от центра по каждой оси. Стрелок выглядывает из-за угла, не выходя из своей
     * клетки; цель видна частично, если виден хотя бы край её клетки. В книге сетки нет (FNFF меряет метрами).
     */
    private static final double[][] OFFSETS = {{0, 0}, {-0.4, -0.4}, {0.4, -0.4}, {-0.4, 0.4}, {0.4, 0.4}};

    /**
     * Видимость цели из клетки стрелка.
     *
     * @param near низкий предмет укрывает цель, если он не дальше {@code near} клеток от неё
     */
    public Sight sight(int fx, int fy, int tx, int ty, int near) {
        Ray center = trace(fx + 0.5, fy + 0.5, tx + 0.5, ty + 0.5, fx, fy, tx, ty, false);
        int exposed = 0;
        List<Material> hiding = new ArrayList<>();
        for (double[] to : OFFSETS) {
            double bx = tx + 0.5 + to[0];
            double by = ty + 0.5 + to[1];
            boolean seen = false;
            for (double[] from : OFFSETS) {
                if (!trace(fx + 0.5 + from[0], fy + 0.5 + from[1], bx, by, fx, fy, tx, ty, true).blocked()) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                exposed++;
            } else {
                // что закрывает эту часть цели: ближайшая к ней преграда на луче из центра стрелка
                trace(fx + 0.5, fy + 0.5, bx, by, fx, fy, tx, ty, false).blockers().stream()
                        .min(Comparator.comparingInt(Obstacle::cellsFromTarget))
                        .map(Obstacle::material).ifPresent(hiding::add);
            }
        }
        Visibility visibility = exposed == SAMPLES ? Visibility.FULL : exposed == 0 ? Visibility.NONE : Visibility.PARTIAL;
        Optional<Material> cover = Optional.empty();
        Optional<Material> through = Optional.empty();
        if (visibility == Visibility.NONE) {
            List<Obstacle> blockers = center.blockers();
            if (blockers.size() == 1 && blockers.get(0).material() != null && blockers.get(0).material().sp() > 0) {
                through = Optional.of(blockers.get(0).material());
            }
        } else {
            List<Material> candidates = new ArrayList<>();
            new Line(false, center.obstacles()).coverNear(near).map(Obstacle::material).ifPresent(candidates::add);
            if (visibility == Visibility.PARTIAL) {
                candidates.addAll(hiding);
            }
            // HOMEBREW: из нескольких укрытий берётся самое прочное
            cover = candidates.stream().filter(material -> material.sp() > 0).max(Comparator.comparingInt(Material::sp));
        }
        return new Sight(visibility, exposed, cover, through, center.path());
    }

    /**
     * Поле видимости: какие клетки видно из клетки стрелка хотя бы краем.
     *
     * @return по строкам сверху вниз, {@code w × h}
     */
    public boolean[] visibleFrom(int fx, int fy) {
        boolean[] visible = new boolean[doc.w() * doc.h()];
        for (int y = 0; y < doc.h(); y++) {
            for (int x = 0; x < doc.w(); x++) {
                visible[y * doc.w() + x] = (x == fx && y == fy) || seesCell(fx, fy, x, y);
            }
        }
        return visible;
    }

    private boolean seesCell(int fx, int fy, int tx, int ty) {
        for (double[] to : OFFSETS) {
            for (double[] from : OFFSETS) {
                if (!trace(fx + 0.5 + from[0], fy + 0.5 + from[1], tx + 0.5 + to[0], ty + 0.5 + to[1], fx, fy, tx, ty, true).blocked()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Луч по клеткам.
     *
     * @param blockers  что закрывает взгляд: стена, закрытая дверь, высокий предмет
     * @param obstacles что видно насквозь или поверх, но с прочностью: окно, машина, ящик
     * @param path      пройденные клетки без первой и последней
     */
    private record Ray(List<Obstacle> blockers, List<Obstacle> obstacles, List<int[]> path) {

        boolean blocked() {
            return !blockers.isEmpty();
        }
    }

    /**
     * Обход Амантидеса — Ву от точки (ax, ay) в клетке (sx, sy) до точки (bx, by) в клетке (tx, ty): каждый шаг пересекает
     * одну грань; через угол двух граней — если открыт хотя бы один обход.
     *
     * @param stopAtBlock остановиться на первой преграде: для поля видимости
     */
    private Ray trace(double ax, double ay, double bx, double by, int sx, int sy, int tx, int ty, boolean stopAtBlock) {
        List<Obstacle> blockers = new ArrayList<>();
        List<Obstacle> obstacles = new ArrayList<>();
        List<int[]> path = new ArrayList<>();
        int x = sx;
        int y = sy;
        double dx = bx - ax;
        double dy = by - ay;
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double tMaxX = dx > 0 ? (sx + 1 - ax) / dx : dx < 0 ? (ax - sx) / -dx : Double.POSITIVE_INFINITY;
        double tMaxY = dy > 0 ? (sy + 1 - ay) / dy : dy < 0 ? (ay - sy) / -dy : Double.POSITIVE_INFINITY;
        final double eps = 1e-9;
        int guard = Math.abs(tx - sx) + Math.abs(ty - sy) + 2;
        while ((x != tx || y != ty) && guard-- > 0) {
            int nx = x;
            int ny = y;
            if (Math.abs(tMaxX - tMaxY) < eps) {
                Optional<Obstacle> viaX = cornerBlock(x, y, x + stepX, y, x + stepX, y + stepY, tx, ty);
                Optional<Obstacle> viaY = cornerBlock(x, y, x, y + stepY, x + stepX, y + stepY, tx, ty);
                if (viaX.isPresent() && viaY.isPresent()) {
                    blockers.add(viaX.get());
                }
                nx = x + stepX;
                ny = y + stepY;
                tMaxX += tDeltaX;
                tMaxY += tDeltaY;
            } else if (tMaxX < tMaxY) {
                nx = x + stepX;
                crossEdge(x, y, nx, ny, tx, ty, blockers, obstacles);
                tMaxX += tDeltaX;
            } else {
                ny = y + stepY;
                crossEdge(x, y, nx, ny, tx, ty, blockers, obstacles);
                tMaxY += tDeltaY;
            }
            x = nx;
            y = ny;
            Optional<Material> material = material(x, y);
            if (x != tx || y != ty) {
                path.add(new int[]{x, y});
                if (material.isPresent()) {
                    Obstacle obstacle = new Obstacle(material.get(), cellsFrom(x, y, tx, ty));
                    (material.get().blocksSight() ? blockers : obstacles).add(obstacle);
                }
            } else if (material.isPresent() && !material.get().blocksSight()) {
                // цель лежит за бордюром в своей клетке
                obstacles.add(new Obstacle(material.get(), 0));
            }
            if (stopAtBlock && !blockers.isEmpty()) {
                break;
            }
        }
        return new Ray(blockers, obstacles, path);
    }

    /** Обход угла через клетку (mx, my) в (ex, ey): что его закрывает; пусто — открыт */
    private Optional<Obstacle> cornerBlock(int x, int y, int mx, int my, int ex, int ey, int tx, int ty) {
        List<Obstacle> blockers = new ArrayList<>();
        crossEdge(x, y, mx, my, tx, ty, blockers, null);
        crossEdge(mx, my, ex, ey, tx, ty, blockers, null);
        if (mx != tx || my != ty) {
            material(mx, my).filter(Material::blocksSight)
                    .ifPresent(material -> blockers.add(new Obstacle(material, cellsFrom(mx, my, tx, ty))));
        }
        return blockers.stream().findFirst();
    }

    /** Грань между соседними клетками: преграда взгляду или препятствие с прочностью (окно) */
    private void crossEdge(int x1, int y1, int x2, int y2, int tx, int ty, List<Obstacle> blockers, List<Obstacle> obstacles) {
        MapDoc.Edge edge = doc.edge(MapDoc.between(x1, y1, x2, y2));
        if (edge == null || edge.kind() == MapDoc.Edge.Kind.GAP || (edge.kind() == MapDoc.Edge.Kind.DOOR && edge.open())) {
            return;
        }
        Material material = edge.material() == null ? null : rules.material(edge.material()).orElse(null);
        Obstacle obstacle = new Obstacle(material, Math.min(cellsFrom(x1, y1, tx, ty), cellsFrom(x2, y2, tx, ty)));
        if (edge.blocksSight()) {
            blockers.add(obstacle);
        } else if (obstacles != null && material != null) {
            obstacles.add(obstacle);
        }
    }

    private static int cellsFrom(int x, int y, int tx, int ty) {
        return Math.max(Math.abs(x - tx), Math.abs(y - ty));
    }

    /**
     * Куда добежать не дальше {@code meters}: шаг по стороне — клетка, по диагонали — клетка × √2; угол стены не срезается.
     *
     * @return клетки {@code {x, y, метров}}, включая начальную; метры пути округлены вверх
     */
    public List<int[]> reach(int fromX, int fromY, int meters) {
        int cell = doc.cell();
        Map<Long, Double> best = new HashMap<>();
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        best.put(pack(fromX, fromY), 0.0);
        queue.add(new double[]{0, fromX, fromY});
        Set<Long> done = new HashSet<>();
        List<int[]> cells = new ArrayList<>();
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        while (!queue.isEmpty()) {
            double[] head = queue.poll();
            int x = (int) head[1];
            int y = (int) head[2];
            if (!done.add(pack(x, y))) {
                continue;
            }
            cells.add(new int[]{x, y, (int) Math.ceil(head[0] - 1e-9)});
            for (int[] step : steps) {
                int nx = x + step[0];
                int ny = y + step[1];
                if (blocked(nx, ny) || !canStep(x, y, nx, ny)) {
                    continue;
                }
                double cost = head[0] + (step[0] != 0 && step[1] != 0 ? Math.sqrt(2) : 1) * cell;
                // полметра запаса: 9 клеток по 2 м при беге 18 м
                if (cost > meters + 0.5 || cost >= best.getOrDefault(pack(nx, ny), Double.MAX_VALUE)) {
                    continue;
                }
                best.put(pack(nx, ny), cost);
                queue.add(new double[]{cost, nx, ny});
            }
        }
        return cells;
    }

    private boolean canStep(int x, int y, int nx, int ny) {
        if (x == nx || y == ny) {
            return !wallBetween(x, y, nx, ny);
        }
        // по диагонали — только если открыты оба обхода
        return !wallBetween(x, y, nx, y) && !wallBetween(nx, y, nx, ny) && !blocked(nx, y)
                && !wallBetween(x, y, x, ny) && !wallBetween(x, ny, nx, ny) && !blocked(x, ny);
    }
}
