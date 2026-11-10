package app.charka.map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Документ карты боя: одна запись JSON в колонке TEXT. Поле {@code v} — версия формата, новые возможности редактора
 * добавляют поля, а не миграции. Незнакомые поля пропускаются.
 * <p>
 * Стены — единичные грани клеток: ключ {@code "x,y,h"} — верхняя грань клетки (x, y), {@code "x,y,v"} — левая; значение —
 * {@link Edge#of}: материал стены, {@code door:<материал>}, {@code door:<материал>:open}, {@code window:<материал>}, {@code gap}.
 * Так общая стена двух комнат одна сама собой, а ластик — удаление ключа.
 *
 * @param v      версия формата
 * @param w      ширина в клетках
 * @param h      высота в клетках
 * @param cell   метров в клетке
 * @param walls  грани: ключ грани → значение {@link Edge}
 * @param cells  клетки: {@code "x,y"} → материал местности (обломки, вода, пол)
 * @param stamps предметы: машина, ящик
 * @param zones  зоны старта
 * @param labels подписи
 * @param bg     где лежит подложка, в клетках; null — подложки нет или она не размещена
 * @param ground M-22: название нижнего уровня (сами стены, клетки, предметы и подписи выше — его); null — «1 этаж»
 * @param levels M-22: уровни выше нижнего по порядку — второй этаж, крыша; того же размера. Пусто — карта плоская
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MapDoc(int v, int w, int h, int cell, Map<String, String> walls, Map<String, String> cells,
                     List<Stamp> stamps, List<Zone> zones, List<Label> labels, Background bg, String ground, List<Floor> levels) {

    public static final int VERSION = 1;
    public static final int MAX_SIZE = 200;

    public static final String PLAYERS = "players";
    public static final String NPCS = "npcs";

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public MapDoc {
        walls = walls == null ? new LinkedHashMap<>() : new LinkedHashMap<>(walls);
        cells = cells == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cells);
        stamps = stamps == null ? new ArrayList<>() : new ArrayList<>(stamps);
        zones = zones == null ? new ArrayList<>() : new ArrayList<>(zones);
        labels = labels == null ? new ArrayList<>() : new ArrayList<>(labels);
        levels = levels == null ? new ArrayList<>() : new ArrayList<>(levels);
    }

    public MapDoc(int v, int w, int h, int cell, Map<String, String> walls, Map<String, String> cells,
                  List<Stamp> stamps, List<Zone> zones, List<Label> labels) {
        this(v, w, h, cell, walls, cells, stamps, zones, labels, null, null, null);
    }

    /** HOMEBREW: высота этажа по умолчанию, м — в книге нет */
    public static final int FLOOR_HEIGHT = 3;

    /**
     * M-22: уровень выше нижнего — свои стены, клетки, предметы и подписи; зоны старта только внизу.
     *
     * @param name название: «2 этаж», «Крыша»
     * @param z    высота пола над нижним уровнем, м
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Floor(String name, int z, Map<String, String> walls, Map<String, String> cells, List<Stamp> stamps, List<Label> labels) {

        public Floor {
            walls = walls == null ? new LinkedHashMap<>() : new LinkedHashMap<>(walls);
            cells = cells == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cells);
            stamps = stamps == null ? new ArrayList<>() : new ArrayList<>(stamps);
            labels = labels == null ? new ArrayList<>() : new ArrayList<>(labels);
        }

        public static Floor empty(String name, int z) {
            return new Floor(name, z, null, null, null, null);
        }
    }

    /** Сколько уровней: нижний и те, что выше */
    public int levelCount() {
        return 1 + levels.size();
    }

    /** Название уровня */
    public String levelName(int level) {
        if (level == 0) {
            return ground == null || ground.isBlank() ? "1 этаж" : ground;
        }
        return levels.get(level - 1).name();
    }

    /** Высота пола уровня, м */
    public int levelZ(int level) {
        return level == 0 ? 0 : levels.get(level - 1).z();
    }

    public boolean hasLevel(int level) {
        return level >= 0 && level < levelCount();
    }

    /**
     * Уровень как отдельная плоская карта: геометрия считается по ней ({@link MapGrid}). Правка вида не меняет документ —
     * верните её {@link #withLevel}.
     *
     * @throws IllegalArgumentException если уровня нет
     */
    public MapDoc level(int level) {
        if (!hasLevel(level)) {
            throw new IllegalArgumentException("Нет такого уровня: " + level);
        }
        if (level == 0) {
            return levels.isEmpty() ? this : new MapDoc(v, w, h, cell, walls, cells, stamps, zones, labels, bg, ground, null);
        }
        Floor floor = levels.get(level - 1);
        return new MapDoc(v, w, h, cell, floor.walls(), floor.cells(), floor.stamps(), null, floor.labels(), null, floor.name(), null);
    }

    /** Документ, в котором уровень заменён правленым видом {@link #level} */
    public MapDoc withLevel(int level, MapDoc view) {
        if (!hasLevel(level)) {
            throw new IllegalArgumentException("Нет такого уровня: " + level);
        }
        if (level == 0) {
            return new MapDoc(v, w, h, cell, view.walls(), view.cells(), view.stamps(), zones, view.labels(), bg, ground, levels);
        }
        List<Floor> floors = new ArrayList<>(levels);
        Floor old = floors.get(level - 1);
        floors.set(level - 1, new Floor(old.name(), old.z(), view.walls(), view.cells(), view.stamps(), view.labels()));
        return new MapDoc(v, w, h, cell, walls, cells, stamps, zones, labels, bg, ground, floors);
    }

    /**
     * Подложка на сетке: левый верхний угол и размер в клетках, дробные — после калибровки.
     *
     * @param opacity прозрачность 0..1
     * @param ver     версия картинки: в адресе, чтобы браузер брал новую, а опрос не читал её из БД
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Background(double x, double y, double w, double h, double opacity, String ver) {
    }

    public MapDoc withBackground(Background background) {
        return new MapDoc(v, w, h, cell, walls, cells, stamps, zones, labels, background, ground, levels);
    }

    /**
     * Предмет на карте.
     *
     * @param code код предмета из {@link MapRules#objects()}
     * @param rot  поворот по часовой: 0, 1, 2, 3 — четверти круга
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stamp(String code, int x, int y, int rot) {
    }

    /**
     * Прямоугольная зона: старт игроков ({@link #PLAYERS}) или NPC ({@link #NPCS}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Zone(String kind, int x, int y, int w, int h) {

        public boolean contains(int cx, int cy) {
            return cx >= x && cy >= y && cx < x + w && cy < y + h;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Label(int x, int y, String text) {
    }

    /**
     * Что стоит на грани.
     *
     * @param kind     стена, дверь, окно или проём
     * @param material код материала {@link MapRules#materials()}; у проёма null
     * @param open     дверь открыта
     */
    public record Edge(Kind kind, String material, boolean open) {

        public enum Kind { WALL, DOOR, WINDOW, GAP }

        public static Edge of(String value) {
            if (value == null || value.isBlank() || "gap".equals(value)) {
                return new Edge(Kind.GAP, null, true);
            }
            String[] parts = value.split(":");
            return switch (parts[0]) {
                case "door" -> new Edge(Kind.DOOR, parts.length > 1 ? parts[1] : null, parts.length > 2 && "open".equals(parts[2]));
                case "window" -> new Edge(Kind.WINDOW, parts.length > 1 ? parts[1] : null, false);
                default -> new Edge(Kind.WALL, parts[0], false);
            };
        }

        @Override
        public String toString() {
            return switch (kind) {
                case GAP -> "gap";
                case WALL -> material;
                case DOOR -> "door:" + material + (open ? ":open" : "");
                case WINDOW -> "window:" + material;
            };
        }

        /** Нельзя пройти: стена, окно и закрытая дверь */
        public boolean blocksMove() {
            return kind == Kind.WALL || kind == Kind.WINDOW || (kind == Kind.DOOR && !open);
        }

        /** Не видно насквозь: стена и закрытая дверь; окно прозрачно */
        public boolean blocksSight() {
            return kind == Kind.WALL || (kind == Kind.DOOR && !open);
        }
    }

    /** Пустая карта */
    public static MapDoc empty(int w, int h, int cell) {
        return new MapDoc(VERSION, w, h, cell, null, null, null, null, null);
    }

    /**
     * @throws IllegalArgumentException если JSON не карта, новее приложения или с неверным размером
     */
    public static MapDoc parse(String json) {
        MapDoc doc;
        try {
            doc = JSON.readValue(json, MapDoc.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Карта не читается: " + e.getOriginalMessage());
        }
        if (doc == null) {
            throw new IllegalArgumentException("Карта пуста");
        }
        if (doc.v() > VERSION) {
            throw new IllegalArgumentException("Карта сохранена более новой версией приложения (формат " + doc.v() + ")");
        }
        doc.validate();
        return doc;
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @throws IllegalArgumentException если размер вне 1..{@link #MAX_SIZE}, ключ грани неверен или за краем
     */
    public void validate() {
        if (w < 1 || h < 1 || w > MAX_SIZE || h > MAX_SIZE) {
            throw new IllegalArgumentException("Размер карты — от 1 до " + MAX_SIZE + " клеток");
        }
        if (cell < 1) {
            throw new IllegalArgumentException("В клетке хотя бы метр");
        }
        walls.keySet().forEach(this::requireEdge);
        cells.keySet().forEach(this::requireCell);
        for (Floor floor : levels) {
            floor.walls().keySet().forEach(this::requireEdge);
            floor.cells().keySet().forEach(this::requireCell);
        }
    }

    private void requireCell(String key) {
        int[] xy = cellOf(key);
        if (!inside(xy[0], xy[1])) {
            throw new IllegalArgumentException("Клетка за краем карты: " + key);
        }
    }

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    /** Грань по ключу; нет ключа — пусто */
    public Edge edge(String key) {
        String value = walls.get(key);
        return value == null ? null : Edge.of(value);
    }

    /** Верхняя грань клетки */
    public static String top(int x, int y) {
        return x + "," + y + ",h";
    }

    /** Левая грань клетки */
    public static String left(int x, int y) {
        return x + "," + y + ",v";
    }

    /**
     * Грань между соседними по стороне клетками.
     *
     * @throws IllegalArgumentException если клетки не соседи
     */
    public static String between(int x1, int y1, int x2, int y2) {
        if (x1 == x2 && Math.abs(y1 - y2) == 1) {
            return top(x1, Math.max(y1, y2));
        }
        if (y1 == y2 && Math.abs(x1 - x2) == 1) {
            return left(Math.max(x1, x2), y1);
        }
        throw new IllegalArgumentException("Клетки не соседи");
    }

    public static String cellKey(int x, int y) {
        return x + "," + y;
    }

    private static int[] cellOf(String key) {
        String[] parts = key.split(",");
        try {
            if (parts.length != 2) {
                throw new NumberFormatException();
            }
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Неверный ключ клетки: " + key);
        }
    }

    private void requireEdge(String key) {
        String[] parts = key.split(",");
        try {
            if (parts.length != 3 || !("h".equals(parts[2]) || "v".equals(parts[2]))) {
                throw new NumberFormatException();
            }
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            // нижняя и правая границы карты — грани клетки за краем
            boolean fits = "h".equals(parts[2]) ? x >= 0 && x < w && y >= 0 && y <= h : x >= 0 && x <= w && y >= 0 && y < h;
            if (!fits) {
                throw new IllegalArgumentException("Грань за краем карты: " + key);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Неверный ключ грани: " + key);
        }
    }
}
