package app.charka.map;

import java.util.ArrayList;
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
     * @param blocked     линию закрывает стена, закрытая дверь или непрозрачная клетка
     * @param obstacles   что пересекает линия, от стрелка к цели: материал и в скольких клетках от цели
     */
    public record Line(boolean blocked, List<Obstacle> obstacles) {

        /**
         * Укрытие цели: ближайшее к цели препятствие с прочностью не дальше {@code near} клеток.
         */
        public Optional<Obstacle> coverNear(int near) {
            Obstacle best = null;
            for (Obstacle obstacle : obstacles) {
                if (obstacle.material().sp() > 0 && obstacle.cellsFromTarget() <= near
                        && (best == null || obstacle.cellsFromTarget() <= best.cellsFromTarget())) {
                    best = obstacle;
                }
            }
            return Optional.ofNullable(best);
        }
    }

    /**
     * @param cellsFromTarget расстояние до цели в клетках по большей оси
     */
    public record Obstacle(Material material, int cellsFromTarget) {
    }

    /**
     * Линия взгляда по клеткам (обход Амантидеса — Ву): каждый шаг пересекает одну грань.
     */
    public Line line(int x1, int y1, int x2, int y2) {
        List<Obstacle> obstacles = new ArrayList<>();
        boolean blocked = false;
        int x = x1;
        int y = y1;
        double dx = x2 - x1;
        double dy = y2 - y1;
        int stepX = Integer.signum(x2 - x1);
        int stepY = Integer.signum(y2 - y1);
        // центры клеток: до первой грани полклетки
        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double tMaxX = tDeltaX / 2;
        double tMaxY = tDeltaY / 2;
        final double eps = 1e-9;
        while (x != x2 || y != y2) {
            int nx = x;
            int ny = y;
            if (Math.abs(tMaxX - tMaxY) < eps) {
                // угол: виден, если открыт хоть один обход
                boolean viaX = edgeSight(x, y, x + stepX, y, x2, y2, null) && edgeSight(x + stepX, y, x + stepX, y + stepY, x2, y2, null)
                        && !cellBlocksSight(x + stepX, y, x2, y2);
                boolean viaY = edgeSight(x, y, x, y + stepY, x2, y2, null) && edgeSight(x, y + stepY, x + stepX, y + stepY, x2, y2, null)
                        && !cellBlocksSight(x, y + stepY, x2, y2);
                if (!viaX && !viaY) {
                    blocked = true;
                }
                nx = x + stepX;
                ny = y + stepY;
                tMaxX += tDeltaX;
                tMaxY += tDeltaY;
            } else if (tMaxX < tMaxY) {
                nx = x + stepX;
                blocked |= !edgeSight(x, y, nx, ny, x2, y2, obstacles);
                tMaxX += tDeltaX;
            } else {
                ny = y + stepY;
                blocked |= !edgeSight(x, y, nx, ny, x2, y2, obstacles);
                tMaxY += tDeltaY;
            }
            x = nx;
            y = ny;
            if (x != x2 || y != y2) {
                Optional<Material> material = material(x, y);
                if (material.isPresent()) {
                    blocked |= material.get().blocksSight();
                    obstacles.add(new Obstacle(material.get(), cellsFrom(x, y, x2, y2)));
                }
            }
        }
        return new Line(blocked, obstacles);
    }

    private boolean cellBlocksSight(int x, int y, int tx, int ty) {
        return (x != tx || y != ty) && material(x, y).map(Material::blocksSight).orElse(false);
    }

    /** @return взгляд проходит грань; препятствие с прочностью записывается */
    private boolean edgeSight(int x1, int y1, int x2, int y2, int tx, int ty, List<Obstacle> obstacles) {
        MapDoc.Edge edge = doc.edge(MapDoc.between(x1, y1, x2, y2));
        if (edge == null || edge.kind() == MapDoc.Edge.Kind.GAP) {
            return true;
        }
        if (obstacles != null && edge.material() != null && !(edge.kind() == MapDoc.Edge.Kind.DOOR && edge.open())) {
            rules.material(edge.material()).ifPresent(material ->
                    obstacles.add(new Obstacle(material, Math.min(cellsFrom(x1, y1, tx, ty), cellsFrom(x2, y2, tx, ty)))));
        }
        return !edge.blocksSight();
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
