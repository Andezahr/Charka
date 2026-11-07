package app.charka.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M-20: текстовая карта — рисунок символами, который удобно написать в текстовом редакторе или попросить у ИИ, прочитать
 * глазами и положить в репозиторий. Формат описан в {@code docs/map-format.md}.
 * <p>
 * Сетка двойная: клетка (x, y) — символ в строке {@code 2y+1}, столбце {@code 2x+1}; между клетками — грани: верхняя грань
 * клетки в строке {@code 2y}, левая — в столбце {@code 2x}. Чётные строка и столбец вместе — угол, он только для глаза.
 * Предмет рисуется своей буквой во всех клетках, поворот угадывается по форме; зоны старта — буквами {@code P} и {@code N};
 * подпись — символом-меткой из раздела {@code labels:}.
 */
public final class MapText {

    public static final String EXTENSION = ".map.txt";

    /** Символы клеток по умолчанию: что стоит в клетке */
    public static final Map<Character, String> CELLS = cells();
    /** Символы граней по умолчанию: инструмент редактора, материал или значение грани {@link MapDoc.Edge} */
    public static final Map<Character, String> EDGES = edges();

    private static final String FLOOR = "floor";
    private static final String NONE = "none";
    private static final String OPEN = ":open";
    private static final int MAX_ERRORS = 15;

    private static final Pattern SIZE = Pattern.compile("(\\d+)\\s*[xх×]\\s*(\\d+)");
    private static final Pattern LEGEND_EDGE = Pattern.compile("edge\\s+(\\S)\\s*=\\s*(\\S+)");
    private static final Pattern LEGEND_CELL = Pattern.compile("(\\S)\\s*=\\s*(\\S+)");
    private static final Pattern LABEL_AT = Pattern.compile("(\\d+)\\s*,\\s*(\\d+)\\s*:\\s*(.+)");
    private static final Pattern LABEL_MARK = Pattern.compile("(\\S)\\s*:\\s*(.+)");

    /** Запасные символы для материалов и предметов, которых нет в легенде по умолчанию */
    private static final String SPARE_CELLS = "&;<>?!()[]{}EJOQSVWYZ";
    /** M-22: раздел рисунка уровня — «map: 1 Второй этаж z=3» */
    private static final Pattern SECTION = Pattern.compile("map:\\s*(?:(\\d+)\\s*)?(.*?)\\s*(?:z\\s*=\\s*(\\d+)\\s*м?)?");
    private static final String SPARE_EDGES = "ABCEFGHIJKLMNOPQRTUVXYZ";
    private static final String LABEL_MARKS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private MapText() {
    }

    private static Map<Character, String> cells() {
        Map<Character, String> map = new LinkedHashMap<>();
        map.put(' ', FLOOR);
        map.put('.', FLOOR);
        map.put(',', "asphalt");
        map.put('"', "grass");
        map.put('~', "water");
        map.put('%', "debris");
        map.put('*', "smoke");
        map.put('F', "fuel_barrel");
        map.put('G', "gas_cylinder");
        // M-22: уровни — лестница на соседний уровень в той же клетке, пустота (видно вниз)
        map.put('^', LevelMap.STAIRS);
        map.put('#', LevelMap.VOID);
        map.put('C', "car");
        map.put('A', "armored_car");
        map.put('X', "crate");
        map.put('B', "dumpster");
        map.put('I', "pole");
        map.put('T', "tree");
        map.put('$', "dataterm");
        map.put('M', "mailbox");
        map.put('H', "hydrant");
        map.put('R', "bar");
        map.put('K', "desk");
        map.put('L', "shelf");
        map.put('U', "container");
        map.put('P', MapDoc.PLAYERS);
        map.put('N', MapDoc.NPCS);
        return java.util.Collections.unmodifiableMap(map);
    }

    private static Map<Character, String> edges() {
        Map<Character, String> map = new LinkedHashMap<>();
        map.put(' ', NONE);
        map.put('-', "room");
        map.put('|', "room");
        map.put('+', "room");
        map.put(':', "wall");
        map.put('!', "wall");
        // бетон; у системы без него — стена комнаты
        map.put('#', "cover_block?room");
        map.put('D', "door");
        map.put('d', "door" + OPEN);
        map.put('S', "heavy_door");
        map.put('s', "heavy_door" + OPEN);
        map.put('=', "window");
        map.put('W', "window");
        map.put('o', "gap");
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * Прочитанная карта.
     *
     * @param name     название из заголовка; null — не задано
     * @param warnings что пропущено: предметы и материалы, которых нет у системы, неровные зоны
     */
    public record Parsed(String name, MapDoc doc, List<String> warnings) {
    }

    private enum Kind { FLOOR, TERRAIN, OBJECT, ZONE, LABEL }

    /**
     * @throws IllegalArgumentException с номерами строк и столбцов, если рисунок не читается
     */
    public static Parsed parse(String text, MapRules rules) {
        return new Reader(text, rules).read();
    }

    /** Значение грани по описанию легенды; null — пусто или у системы нет материала */
    static String edgeValue(String spec, MapRules rules) {
        for (String option : spec.split("\\?")) {
            boolean open = option.endsWith(OPEN);
            String code = open ? option.substring(0, option.length() - OPEN.length()) : option;
            String value = switch (code) {
                case NONE -> "";
                case "gap" -> "gap";
                case "room", "wall" -> rules.defaults().get(code);
                case "door", "heavy_door" -> rules.defaults().get(code) == null ? null : "door:" + rules.defaults().get(code);
                case "window" -> rules.defaults().get(code) == null ? null : "window:" + rules.defaults().get(code);
                default -> materialEdge(code, rules);
            };
            if (value != null) {
                return value.startsWith("door:") && open ? value + OPEN : value;
            }
        }
        return null;
    }

    private static String materialEdge(String code, MapRules rules) {
        if (code.startsWith("door:") || code.startsWith("window:")) {
            String material = code.split(":")[1];
            return rules.material(material).isPresent() ? code : null;
        }
        return rules.material(code).map(material -> switch (material.layer()) {
            case DOOR -> "door:" + code;
            case WINDOW -> "window:" + code;
            default -> code;
        }).orElse(null);
    }

    private static final class Reader {

        private final String[] lines;
        private final MapRules rules;
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Map<Character, String> cellLegend = new HashMap<>(CELLS);
        private final Map<Character, String> edgeLegend = new HashMap<>(EDGES);
        private final Map<Character, String> marks = new HashMap<>();
        private final List<MapDoc.Label> placedLabels = new ArrayList<>();
        private final Set<String> warned = new HashSet<>();
        private String name;
        private Integer width;
        private Integer height;
        private Integer cell;
        private int mapLine = -1;

        Reader(String text, MapRules rules) {
            this.lines = (text == null ? "" : text.replace("﻿", "")).split("\r?\n", -1);
            this.rules = rules;
        }

        /** Строки файла, где уже три ошибки: дальше по ним — одна подсказка, а не столбец за столбцом */
        private final Map<Integer, Integer> errorsInLine = new HashMap<>();

        private void error(int line, int column, String message) {
            int count = errorsInLine.merge(line, 1, Integer::sum);
            if (count == 3 && column >= 0) {
                message = "много символов не на своих местах — клетки рисуются в нечётных строках рисунка, стены — в чётных"
                        + " между ними (пустая строка, если стен нет). Пропущена или лишняя строка?";
                column = -1;
            } else if (count > 3) {
                return;
            }
            if (errors.size() < MAX_ERRORS) {
                errors.add("Строка " + (line + 1) + (column >= 0 ? ", столбец " + (column + 1) : "") + ": " + message);
            } else if (errors.size() == MAX_ERRORS) {
                errors.add("…и дальше");
            }
        }

        private void warnOnce(String key, String message) {
            if (warned.add(key)) {
                warnings.add(message);
            }
        }

        Parsed read() {
            header();
            if (mapLine < 0) {
                throw new IllegalArgumentException("Нет раздела «map:» с рисунком карты");
            }
            // M-22: каждый раздел «map:» — уровень, снизу вверх
            List<Integer> starts = new ArrayList<>();
            for (int i = mapLine; i < lines.length; i++) {
                if (lines[i].strip().startsWith("map:") && SECTION.matcher(lines[i].strip()).matches()) {
                    starts.add(i);
                }
            }
            MapDoc ground = null;
            String groundName = null;
            List<MapDoc.Floor> floors = new ArrayList<>();
            for (int section = 0; section < starts.size(); section++) {
                int start = starts.get(section);
                int end = section + 1 < starts.size() ? starts.get(section + 1) : lines.length;
                mapLine = start;
                java.util.regex.Matcher title = SECTION.matcher(lines[start].strip());
                title.matches();
                String levelName = title.group(2) == null || title.group(2).isBlank() ? null : title.group(2).strip();
                MapDoc level = grid(start, end, ground);
                if (ground == null) {
                    ground = level;
                    groundName = levelName;
                    continue;
                }
                if (!level.zones().isEmpty()) {
                    warnings.add("Уровень «" + (levelName == null ? section + 1 + " этаж" : levelName) + "»: зоны старта — только на нижнем, пропущены");
                }
                int z = title.group(3) != null ? Integer.parseInt(title.group(3)) : section * MapDoc.FLOOR_HEIGHT;
                floors.add(new MapDoc.Floor(levelName == null ? (section + 1) + " этаж" : levelName, z,
                        level.walls(), level.cells(), level.stamps(), level.labels()));
            }
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(String.join("\n", errors));
            }
            for (MapDoc.Label label : placedLabels) {
                if (ground.inside(label.x(), label.y())) {
                    ground.labels().add(label);
                } else {
                    warnings.add("Подпись «" + label.text() + "» в клетке " + label.x() + "," + label.y() + " за краем карты пропущена");
                }
            }
            MapDoc doc = new MapDoc(ground.v(), ground.w(), ground.h(), ground.cell(), ground.walls(), ground.cells(), ground.stamps(),
                    ground.zones(), ground.labels(), null, starts.size() > 1 ? groundName : null, floors);
            doc.validate();
            return new Parsed(name, doc, List.copyOf(warnings));
        }

        /**
         * Рисунок одного раздела: строки после «map:» до следующего раздела.
         *
         * @param ground нижний уровень: размер выше — его; null — это он и есть
         */
        private MapDoc grid(int start, int end, MapDoc ground) {
            List<String> grid = new ArrayList<>();
            for (int i = start + 1; i < end; i++) {
                grid.add(lines[i]);
            }
            while (!grid.isEmpty() && grid.getLast().isBlank()) {
                grid.removeLast();
            }
            int longest = grid.stream().mapToInt(String::length).max().orElse(0);
            Integer fixedW = ground != null ? Integer.valueOf(ground.w()) : width;
            Integer fixedH = ground != null ? Integer.valueOf(ground.h()) : height;
            int w = fixedW != null ? fixedW : longest / 2;
            int h = fixedH != null ? fixedH : grid.size() / 2;
            if (w < 1 || h < 1) {
                throw new IllegalArgumentException("Рисунок карты пуст");
            }
            if (fixedW != null && longest > 2 * w + 1) {
                for (int r = 0; r < grid.size(); r++) {
                    if (grid.get(r).stripTrailing().length() > 2 * w + 1) {
                        error(mapLine + 1 + r, 2 * w + 1, "строка длиннее ширины " + w + " (" + (2 * w + 1) + " символов)");
                    }
                }
            }
            if (fixedH != null && grid.size() > 2 * h + 1) {
                error(mapLine + 2 + 2 * h, -1, "строк больше высоты " + h + " (" + (2 * h + 1) + " строк рисунка)");
            }
            unevenRows(grid);
            MapDoc doc = MapDoc.empty(w, h, ground != null ? ground.cell() : cell != null ? cell : rules.cellMeters());
            doc.validate();
            Grid g = new Grid(grid, w, h);
            edgesOf(g, doc);
            cellsOf(g, doc);
            return doc;
        }

        /**
         * Ряд клеток короче или длиннее большинства — чаще всего пропущена или лишняя клетка, и всё правее съехало.
         * Только предупреждение: у карты, где пол — пробелы, ряды бывают короче законно.
         */
        private void unevenRows(List<String> grid) {
            Map<Integer, Integer> counts = new HashMap<>();
            int rows = 0;
            for (int r = 1; r < grid.size(); r += 2) {
                if (!grid.get(r).isBlank()) {
                    counts.merge(grid.get(r).stripTrailing().length() / 2, 1, Integer::sum);
                    rows++;
                }
            }
            int usual = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
            if (rows < 3 || counts.getOrDefault(usual, 0) * 2 < rows) {
                return;
            }
            for (int r = 1; r < grid.size(); r += 2) {
                int cells = grid.get(r).stripTrailing().length() / 2;
                if (!grid.get(r).isBlank() && cells != usual) {
                    warnings.add("Строка " + (mapLine + 2 + r) + ": клеток " + cells + ", в остальных рядах " + usual
                            + " — " + (cells < usual ? "пропущена" : "лишняя") + " клетка? Всё правее неё съехало");
                }
            }
        }

        private void header() {
            String section = "";
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].strip();
                if (line.equals("map:") || SECTION.matcher(line).matches() && line.startsWith("map:")) {
                    mapLine = i;
                    return;
                }
                // «#: текст» в подписях — метка «#», не комментарий
                if (line.isEmpty() || line.startsWith("#") && !(section.equals("labels") && line.matches("#\\s*:.*"))) {
                    continue;
                }
                if (line.equals("legend:") || line.equals("labels:")) {
                    section = line.substring(0, line.length() - 1);
                    continue;
                }
                switch (section) {
                    case "legend" -> legend(i, stripComment(line));
                    case "labels" -> label(i, line);
                    default -> headerLine(i, line);
                }
            }
        }

        private static String stripComment(String line) {
            int hash = line.indexOf(" #");
            return hash < 0 ? line : line.substring(0, hash).strip();
        }

        private void headerLine(int i, String line) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                error(i, -1, "ожидалось «ключ: значение», «legend:», «labels:» или «map:»");
                return;
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            switch (key) {
                case "name" -> name = value.isEmpty() ? null : value;
                case "size" -> {
                    Matcher m = SIZE.matcher(value);
                    if (m.matches()) {
                        width = Integer.parseInt(m.group(1));
                        height = Integer.parseInt(m.group(2));
                    } else {
                        error(i, -1, "размер пишется как «70x50»");
                    }
                }
                case "cell" -> {
                    try {
                        cell = Integer.parseInt(value.replace("m", "").replace("м", "").strip());
                    } catch (NumberFormatException e) {
                        error(i, -1, "клетка — целое число метров");
                    }
                }
                default -> warnings.add("Строка " + (i + 1) + ": неизвестный ключ «" + key + "» пропущен");
            }
        }

        private void legend(int i, String line) {
            Matcher edge = LEGEND_EDGE.matcher(line);
            if (edge.matches()) {
                edgeLegend.put(edge.group(1).charAt(0), edge.group(2));
                return;
            }
            Matcher cellMatch = LEGEND_CELL.matcher(line);
            if (cellMatch.matches()) {
                cellLegend.put(cellMatch.group(1).charAt(0), cellMatch.group(2));
                return;
            }
            error(i, -1, "легенда: «X = crate» для клетки или «edge E = cover_stone» для грани");
        }

        private void label(int i, String line) {
            Matcher at = LABEL_AT.matcher(line);
            if (at.matches()) {
                placedLabels.add(new MapDoc.Label(Integer.parseInt(at.group(1)), Integer.parseInt(at.group(2)), text(at.group(3))));
                return;
            }
            Matcher mark = LABEL_MARK.matcher(line);
            if (mark.matches()) {
                marks.put(mark.group(1).charAt(0), text(mark.group(2)));
                return;
            }
            error(i, -1, "подпись: «a: Склад» (метка в рисунке) или «12,7: Склад» (клетка)");
        }

        private static String text(String raw) {
            String value = raw.strip();
            return value.length() > 40 ? value.substring(0, 40) : value;
        }

        /** Рисунок с номерами строк файла для сообщений */
        private final class Grid {
            final List<String> rows;
            final int w;
            final int h;

            Grid(List<String> rows, int w, int h) {
                this.rows = rows;
                this.w = w;
                this.h = h;
            }

            char at(int r, int c) {
                if (r >= rows.size()) {
                    return ' ';
                }
                String row = rows.get(r);
                return c < row.length() ? row.charAt(c) : ' ';
            }

            int line(int r) {
                return mapLine + 1 + r;
            }
        }

        private void edgesOf(Grid g, MapDoc doc) {
            Map<Character, String> resolved = new HashMap<>();
            for (int y = 0; y <= g.h; y++) {
                for (int x = 0; x <= g.w; x++) {
                    if (x < g.w) {
                        edge(g, doc, resolved, 2 * y, 2 * x + 1, MapDoc.top(x, y));
                    }
                    if (y < g.h) {
                        edge(g, doc, resolved, 2 * y + 1, 2 * x, MapDoc.left(x, y));
                    }
                }
            }
        }

        private void edge(Grid g, MapDoc doc, Map<Character, String> resolved, int r, int c, String key) {
            char ch = g.at(r, c);
            if (ch == '\t') {
                error(g.line(r), c, "табуляция: рисуйте пробелами, иначе столбцы съедут");
                return;
            }
            String spec = edgeLegend.get(ch);
            if (spec == null) {
                error(g.line(r), c, "«" + ch + "» на месте стены. Стены: - | : # D d S s = o; клетка — на нечётном месте (съехал столбец?)");
                return;
            }
            String value = resolved.computeIfAbsent(ch, k -> {
                String v = edgeValue(spec, rules);
                return v == null ? "" : v;
            });
            if (value.isEmpty()) {
                if (!NONE.equals(spec)) {
                    warnOnce("edge" + ch, "Стена «" + ch + "» (" + spec + "): у системы нет такого материала, грань пропущена");
                }
                return;
            }
            doc.walls().put(key, value);
        }

        private void cellsOf(Grid g, MapDoc doc) {
            int w = g.w;
            int h = g.h;
            Kind[][] kind = new Kind[w][h];
            String[][] what = new String[w][h];
            char[][] chars = new char[w][h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r = 2 * y + 1;
                    int c = 2 * x + 1;
                    char ch = g.at(r, c);
                    chars[x][y] = ch;
                    if (ch == '\t') {
                        error(g.line(r), c, "табуляция: рисуйте пробелами, иначе столбцы съедут");
                        kind[x][y] = Kind.FLOOR;
                        continue;
                    }
                    if (marks.containsKey(ch)) {
                        kind[x][y] = Kind.LABEL;
                        what[x][y] = marks.get(ch);
                        continue;
                    }
                    String spec = cellLegend.get(ch);
                    if (spec == null) {
                        error(g.line(r), c, "«" + ch + "» нет в легенде" + (EDGES.containsKey(ch) ? " клеток: это стена, а здесь место клетки (съехал столбец?)" : ""));
                        kind[x][y] = Kind.FLOOR;
                    } else if (FLOOR.equals(spec)) {
                        kind[x][y] = Kind.FLOOR;
                    } else if (MapDoc.PLAYERS.equals(spec) || MapDoc.NPCS.equals(spec)) {
                        kind[x][y] = Kind.ZONE;
                        what[x][y] = spec;
                    } else if (rules.object(spec).isPresent()) {
                        kind[x][y] = Kind.OBJECT;
                        what[x][y] = spec;
                    } else if (rules.material(spec).isPresent()) {
                        kind[x][y] = Kind.TERRAIN;
                        what[x][y] = spec;
                    } else {
                        warnOnce("cell" + ch, "«" + ch + "» (" + spec + "): у системы нет такого предмета или материала, клетки — пол");
                        kind[x][y] = Kind.FLOOR;
                    }
                }
            }
            // местность под предметом, зоной и подписью — как у ближайшей обычной клетки слева в ряду, иначе справа
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    String terrain = kind[x][y] == Kind.TERRAIN ? what[x][y] : null;
                    if (kind[x][y] != Kind.FLOOR && kind[x][y] != Kind.TERRAIN) {
                        terrain = groundNear(kind, what, x, y, w);
                    }
                    if (terrain != null) {
                        doc.cells().put(MapDoc.cellKey(x, y), terrain);
                    }
                    if (kind[x][y] == Kind.LABEL) {
                        doc.labels().add(new MapDoc.Label(x, y, what[x][y]));
                    }
                }
            }
            objects(g, doc, kind, what, chars);
            zones(doc, kind, what, chars);
        }

        private static String groundNear(Kind[][] kind, String[][] what, int x, int y, int w) {
            for (int step : new int[]{-1, 1}) {
                for (int i = x + step; i >= 0 && i < w; i += step) {
                    if (kind[i][y] == Kind.TERRAIN) {
                        return what[i][y];
                    }
                    if (kind[i][y] == Kind.FLOOR) {
                        return null;
                    }
                }
            }
            return null;
        }

        /** Клетки одной буквы раскладываются на предметы слева направо, сверху вниз: сначала как нарисован предмет, потом повёрнутым */
        private void objects(Grid g, MapDoc doc, Kind[][] kind, String[][] what, char[][] chars) {
            boolean[][] used = new boolean[g.w][g.h];
            for (int y = 0; y < g.h; y++) {
                for (int x = 0; x < g.w; x++) {
                    if (kind[x][y] != Kind.OBJECT || used[x][y]) {
                        continue;
                    }
                    MapObject object = rules.object(what[x][y]).orElseThrow();
                    int rot = fits(chars, used, x, y, object.width(0), object.height(0), g) ? 0
                            : fits(chars, used, x, y, object.width(1), object.height(1), g) ? 1 : -1;
                    if (rot < 0) {
                        error(g.line(2 * y + 1), 2 * x + 1, "«" + chars[x][y] + "» — " + object.label() + " " + object.w() + "×" + object.h()
                                + " клетки: буквой надо закрыть предмет целиком (" + object.w() + " в ширину и " + object.h()
                                + " в высоту или наоборот)");
                        used[x][y] = true;
                        continue;
                    }
                    for (int dy = 0; dy < object.height(rot); dy++) {
                        for (int dx = 0; dx < object.width(rot); dx++) {
                            used[x + dx][y + dy] = true;
                            if (dx > 0 && doc.walls().containsKey(MapDoc.left(x + dx, y + dy))
                                    || dy > 0 && doc.walls().containsKey(MapDoc.top(x + dx, y + dy))) {
                                warnOnce("cut" + x + "," + y, "Строка " + (g.line(2 * y + 1) + 1) + ": " + object.label() + " разрезан стеной");
                            }
                        }
                    }
                    doc.stamps().add(new MapDoc.Stamp(object.code(), x, y, rot));
                }
            }
        }

        private static boolean fits(char[][] chars, boolean[][] used, int x, int y, int w, int h, Grid g) {
            if (x + w > g.w || y + h > g.h) {
                return false;
            }
            for (int dy = 0; dy < h; dy++) {
                for (int dx = 0; dx < w; dx++) {
                    if (used[x + dx][y + dy] || chars[x + dx][y + dy] != chars[x][y]) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Связные клетки одной буквы зоны — одна зона, неровная дополняется до прямоугольника */
        private void zones(MapDoc doc, Kind[][] kind, String[][] what, char[][] chars) {
            int w = kind.length;
            int h = w == 0 ? 0 : kind[0].length;
            boolean[][] seen = new boolean[w][h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (kind[x][y] != Kind.ZONE || seen[x][y]) {
                        continue;
                    }
                    int minX = x, maxX = x, minY = y, maxY = y, count = 0;
                    java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
                    queue.add(new int[]{x, y});
                    seen[x][y] = true;
                    while (!queue.isEmpty()) {
                        int[] c = queue.poll();
                        count++;
                        minX = Math.min(minX, c[0]);
                        maxX = Math.max(maxX, c[0]);
                        minY = Math.min(minY, c[1]);
                        maxY = Math.max(maxY, c[1]);
                        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int nx = c[0] + d[0];
                            int ny = c[1] + d[1];
                            if (nx >= 0 && ny >= 0 && nx < w && ny < h && !seen[nx][ny] && kind[nx][ny] == Kind.ZONE
                                    && chars[nx][ny] == chars[x][y]) {
                                seen[nx][ny] = true;
                                queue.add(new int[]{nx, ny});
                            }
                        }
                    }
                    if (count != (maxX - minX + 1) * (maxY - minY + 1)) {
                        warnings.add("Зона «" + chars[x][y] + "» у клетки " + x + "," + y + " не прямоугольная — взят охватывающий прямоугольник");
                    }
                    doc.zones().add(new MapDoc.Zone(what[x][y], minX, minY, maxX - minX + 1, maxY - minY + 1));
                }
            }
        }
    }

    /**
     * Карта текстом: обратное к {@link #parse}. Поворот предмета на пол-оборота и подложка не сохраняются.
     */
    public static String write(String name, MapDoc whole, MapRules rules) {
        int w = whole.w();
        int h = whole.h();
        Map<String, Character> cellChars = new LinkedHashMap<>();
        CELLS.forEach((ch, spec) -> {
            if (ch != ' ') {
                cellChars.putIfAbsent(spec, ch);
            }
        });
        Map<String, Character> edgeChars = new LinkedHashMap<>();
        EDGES.forEach((ch, spec) -> {
            String value = edgeValue(spec, rules);
            if (value != null && !value.isEmpty() && ch != '|' && ch != '+' && ch != '!') {
                edgeChars.putIfAbsent(value, ch);
            }
        });
        String roomValue = edgeValue("room", rules);
        List<String> cellLegend = new ArrayList<>();
        List<String> edgeLegend = new ArrayList<>();
        int[] spareCell = {0};
        int[] spareEdge = {0};

        List<String> labelLines = new ArrayList<>();
        Map<String, Character> labelMarks = new LinkedHashMap<>();
        int[] nextMark = {0};
        // M-22: уровни — по разделу «map:» снизу вверх, легенда общая
        List<char[][]> grids = new ArrayList<>();
        for (int level = 0; level < whole.levelCount(); level++) {
            MapDoc doc = whole.level(level);
            boolean ground = level == 0;
            char[][] grid = new char[2 * h + 1][2 * w + 1];
            for (char[] row : grid) {
                java.util.Arrays.fill(row, ' ');
            }
            // грани
            doc.walls().forEach((key, value) -> {
                String[] parts = key.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                boolean horizontal = "h".equals(parts[2]);
                Character ch = edgeChars.get(value);
                if (ch == null && spareEdge[0] < SPARE_EDGES.length()) {
                    ch = SPARE_EDGES.charAt(spareEdge[0]++);
                    edgeChars.put(value, ch);
                    edgeLegend.add("edge " + ch + " = " + value);
                }
                if (ch == null) {
                    return;
                }
                if (ch == '-' && value.equals(roomValue) && !horizontal) {
                    ch = '|';
                }
                if (horizontal) {
                    grid[2 * y][2 * x + 1] = ch;
                } else {
                    grid[2 * y + 1][2 * x] = ch;
                }
            });
            // углы — для глаза
            for (int r = 0; r <= 2 * h; r += 2) {
                for (int c = 0; c <= 2 * w; c += 2) {
                    boolean wall = (c > 0 && grid[r][c - 1] != ' ') || (c < 2 * w && grid[r][c + 1] != ' ')
                            || (r > 0 && grid[r - 1][c] != ' ') || (r < 2 * h && grid[r + 1][c] != ' ');
                    if (wall) {
                        grid[r][c] = '+';
                    }
                }
            }
            // клетки: местность, предметы, зоны
            boolean[][] busy = new boolean[w][h];
            doc.cells().forEach((key, material) -> {
                String[] parts = key.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                Character ch = spareFor(material, cellChars, cellLegend, spareCell);
                if (ch != null) {
                    grid[2 * y + 1][2 * x + 1] = ch;
                }
            });
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (grid[2 * y + 1][2 * x + 1] == ' ') {
                        grid[2 * y + 1][2 * x + 1] = '.';
                    }
                }
            }
            for (MapDoc.Stamp stamp : doc.stamps()) {
                MapObject object = rules.object(stamp.code()).orElse(null);
                Character ch = object == null ? null : spareFor(stamp.code(), cellChars, cellLegend, spareCell);
                if (ch == null) {
                    continue;
                }
                for (int dy = 0; dy < object.height(stamp.rot()); dy++) {
                    for (int dx = 0; dx < object.width(stamp.rot()); dx++) {
                        int x = stamp.x() + dx;
                        int y = stamp.y() + dy;
                        if (doc.inside(x, y)) {
                            grid[2 * y + 1][2 * x + 1] = ch;
                            busy[x][y] = true;
                        }
                    }
                }
            }
            for (MapDoc.Zone zone : doc.zones()) {
                Character ch = cellChars.get(zone.kind());
                for (int y = zone.y(); y < zone.y() + zone.h(); y++) {
                    for (int x = zone.x(); x < zone.x() + zone.w(); x++) {
                        if (ch != null && doc.inside(x, y) && !busy[x][y]) {
                            grid[2 * y + 1][2 * x + 1] = ch;
                            busy[x][y] = true;
                        }
                    }
                }
            }
            // подписи: меткой в рисунке, если клетка свободна, иначе координатами
            // по рядам, как их читает parse: второй круг даёт тот же текст
            List<MapDoc.Label> labels = new ArrayList<>(doc.labels());
            labels.sort(java.util.Comparator.comparingInt(MapDoc.Label::y).thenComparingInt(MapDoc.Label::x));
            for (MapDoc.Label label : labels) {
                boolean free = doc.inside(label.x(), label.y()) && !busy[label.x()][label.y()];
                Character mark = labelMarks.get(label.text());
                if (free && mark == null && nextMark[0] < LABEL_MARKS.length()) {
                    mark = LABEL_MARKS.charAt(nextMark[0]++);
                    labelMarks.put(label.text(), mark);
                    labelLines.add("  " + mark + ": " + label.text());
                }
                if (free && mark != null) {
                    grid[2 * label.y() + 1][2 * label.x() + 1] = mark;
                    busy[label.x()][label.y()] = true;
                } else if (ground) {
                    labelLines.add("  " + label.x() + "," + label.y() + ": " + label.text());
                }
            }
            grids.add(grid);
        }

        StringBuilder out = new StringBuilder();
        out.append("# Charka — текстовая карта. Формат: docs/map-format.md\n");
        out.append("name: ").append(name == null ? "" : name).append('\n');
        out.append("size: ").append(w).append('x').append(h).append('\n');
        out.append("cell: ").append(whole.cell()).append('\n');
        if (!cellLegend.isEmpty() || !edgeLegend.isEmpty()) {
            out.append("legend:\n");
            cellLegend.forEach(line -> out.append("  ").append(line).append('\n'));
            edgeLegend.forEach(line -> out.append("  ").append(line).append('\n'));
        }
        if (!labelLines.isEmpty()) {
            out.append("labels:\n");
            labelLines.forEach(line -> out.append(line).append('\n'));
        }
        for (int level = 0; level < grids.size(); level++) {
            if (whole.levelCount() == 1) {
                out.append("map:\n");
            } else {
                int z = whole.levelZ(level);
                boolean usual = z == level * MapDoc.FLOOR_HEIGHT;
                out.append("map: ").append(level).append(' ').append(whole.levelName(level)).append(usual ? "" : " z=" + z).append('\n');
            }
            for (char[] row : grids.get(level)) {
                out.append(new String(row).stripTrailing()).append('\n');
            }
        }
        return out.toString();
    }

    private static Character spareFor(String code, Map<String, Character> chars, List<String> legend, int[] spare) {
        Character ch = chars.get(code);
        if (ch == null && spare[0] < SPARE_CELLS.length()) {
            ch = SPARE_CELLS.charAt(spare[0]++);
            chars.put(code, ch);
            legend.add(ch + " = " + code);
        }
        return ch;
    }
}
