package app.charka.map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Генератор карт (M-08): улица, бар, склад, парковка, коридоры, крыши, пустая. Раскладка — ядро, материалы и предметы
 * берутся из {@link MapRules} системы; предмета, которого у системы нет, на карте просто не будет. Одно зерно — одна карта.
 * <p>
 * M-19: большие готовые локации — перекрёсток, порт, офис. План у них постоянный и размер свой, зерно меняет только
 * расстановку машин, ящиков, контейнеров и столов.
 * <p>
 * M-20: нарисованные карты — файлы {@code classpath:maps/*.map.txt} в формате {@link MapText}; код шаблона — имя файла,
 * название и размер — из заголовка. Файл, который не читается, в список не попадает (ошибка в журнале).
 */
@Slf4j
@Component
public class MapGenerator {

    static final String FILES = "classpath*:maps/*" + MapText.EXTENSION;

    /**
     * @param code   код шаблона
     * @param label  название
     * @param width  размер по умолчанию, клеток
     * @param height размер по умолчанию, клеток
     * @param fixed  готовая локация: размер всегда свой, заданный пропускается
     */
    public record Template(String code, String label, int width, int height, boolean fixed) {

        public Template(String code, String label, int width, int height) {
            this(code, label, width, height, false);
        }
    }

    public static final List<Template> TEMPLATES = List.of(
            new Template("street", "Улица", 30, 18),
            new Template("bar", "Бар", 24, 18),
            new Template("warehouse", "Склад", 28, 20),
            new Template("parking", "Парковка", 26, 18),
            new Template("corridors", "Коридоры", 28, 20),
            new Template("roofs", "Крыши", 30, 20),
            new Template("crossroads", "Перекрёсток", 70, 50, true),
            new Template("port", "Порт", 80, 50, true),
            new Template("office", "Офис корпорации", 60, 40, true),
            new Template("empty", "Пустая", 30, 20));

    /** Нарисованные карты: код → текст */
    private final Map<String, String> drawn = new LinkedHashMap<>();
    private final List<Template> templates;

    public MapGenerator() {
        this(FILES);
    }

    MapGenerator(String pattern) {
        List<Template> fromFiles = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
            java.util.Arrays.sort(resources, Comparator.comparing(resource -> String.valueOf(resource.getFilename())));
            for (Resource resource : resources) {
                String file = String.valueOf(resource.getFilename());
                String code = file.substring(0, file.length() - MapText.EXTENSION.length());
                try {
                    String text = resource.getContentAsString(StandardCharsets.UTF_8);
                    MapText.Parsed parsed = MapText.parse(text, MapRules.GENERIC);
                    drawn.put(code, text);
                    fromFiles.add(new Template(code, parsed.name() == null ? code : parsed.name(),
                            parsed.doc().w(), parsed.doc().h(), true));
                } catch (IllegalArgumentException | IOException e) {
                    log.error("Карта {} не читается: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Нарисованные карты не найдены: {}", e.getMessage());
        }
        List<Template> all = new ArrayList<>(TEMPLATES.subList(0, TEMPLATES.size() - 1));
        all.addAll(fromFiles);
        all.add(TEMPLATES.getLast());
        templates = List.copyOf(all);
    }

    /** Шаблоны генератора и нарисованные карты; «Пустая» — последней */
    public List<Template> templates() {
        return templates;
    }

    /** Нарисованные карты: код → текст {@link MapText} */
    public Map<String, String> drawn() {
        return java.util.Collections.unmodifiableMap(drawn);
    }

    /**
     * Шаблон генератора (без нарисованных карт).
     *
     * @throws IllegalArgumentException если такого нет
     */
    public static Template template(String code) {
        return TEMPLATES.stream().filter(template -> template.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет такого шаблона карты: " + code));
    }

    /**
     * Шаблон генератора или нарисованная карта.
     *
     * @throws IllegalArgumentException если такого нет
     */
    public Template templateOf(String code) {
        return templates.stream().filter(template -> template.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет такого шаблона карты: " + code));
    }

    /**
     * @throws IllegalArgumentException если шаблона нет или размер вне пределов
     */
    public MapDoc generate(String code, int width, int height, long seed, MapRules rules) {
        Template template = templateOf(code);
        if (drawn.containsKey(code)) {
            return MapText.parse(drawn.get(code), rules).doc();
        }
        if (template.fixed()) {
            width = template.width();
            height = template.height();
        }
        MapDoc doc = MapDoc.empty(width, height, rules.cellMeters());
        doc.validate();
        Builder b = new Builder(doc, rules, new Random(seed));
        switch (code) {
            case "street" -> b.street();
            case "bar" -> b.bar();
            case "warehouse" -> b.warehouse();
            case "parking" -> b.parking();
            case "corridors" -> b.corridors();
            case "roofs" -> b.roofs();
            case "crossroads" -> b.crossroads();
            case "port" -> b.port();
            case "office" -> b.office();
            default -> b.zonesAtEnds();
        }
        doc.validate();
        return doc;
    }

    /** Раскладка на одну карту: что уже занято и случайности по зерну */
    private static final class Builder {

        private final MapDoc doc;
        private final MapRules rules;
        private final Random random;
        private final Set<Long> taken = new HashSet<>();

        Builder(MapDoc doc, MapRules rules, Random random) {
            this.doc = doc;
            this.rules = rules;
            this.random = random;
        }

        private String material(String tool) {
            return rules.defaults().get(tool);
        }

        private int between(int min, int max) {
            return max <= min ? min : min + random.nextInt(max - min + 1);
        }

        /** Стены по краю прямоугольника клеток; уже стоящие двери и окна не трогает */
        void room(int x, int y, int w, int h, String material) {
            for (int i = x; i < x + w; i++) {
                wall(MapDoc.top(i, y), material);
                wall(MapDoc.top(i, y + h), material);
            }
            for (int j = y; j < y + h; j++) {
                wall(MapDoc.left(x, j), material);
                wall(MapDoc.left(x + w, j), material);
            }
        }

        private void wall(String key, String material) {
            if (fitsEdge(key)) {
                doc.walls().putIfAbsent(key, material);
            }
        }

        private boolean fitsEdge(String key) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            return "h".equals(parts[2]) ? x >= 0 && x < doc.w() && y >= 0 && y <= doc.h() : x >= 0 && x <= doc.w() && y >= 0 && y < doc.h();
        }

        void door(String key, String material) {
            if (fitsEdge(key)) {
                doc.walls().put(key, "door:" + material);
            }
        }

        void window(String key) {
            if (fitsEdge(key) && material("window") != null) {
                doc.walls().put(key, "window:" + material("window"));
            }
        }

        /** Предмет, если он есть у системы и встаёт на свободное место */
        boolean stamp(String code, int x, int y, int rot) {
            MapObject object = rules.object(code).orElse(null);
            if (object == null) {
                return false;
            }
            int w = object.width(rot);
            int h = object.height(rot);
            for (int dy = 0; dy < h; dy++) {
                for (int dx = 0; dx < w; dx++) {
                    if (!doc.inside(x + dx, y + dy) || taken.contains(key(x + dx, y + dy))) {
                        return false;
                    }
                }
            }
            for (int dy = 0; dy < h; dy++) {
                for (int dx = 0; dx < w; dx++) {
                    taken.add(key(x + dx, y + dy));
                }
            }
            doc.stamps().add(new MapDoc.Stamp(code, x, y, rot));
            return true;
        }

        /** Клетки, на которые нельзя ставить предметы: проход у двери, зона старта */
        private void reserve(int x, int y, int w, int h) {
            for (int j = y; j < y + h; j++) {
                for (int i = x; i < x + w; i++) {
                    taken.add(key(i, j));
                }
            }
        }

        private static long key(int x, int y) {
            return ((long) x << 32) | (y & 0xffffffffL);
        }

        void zone(String kind, int x, int y, int w, int h) {
            int cx = Math.max(0, x);
            int cy = Math.max(0, y);
            int cw = Math.min(doc.w(), x + w) - cx;
            int ch = Math.min(doc.h(), y + h) - cy;
            if (cw > 0 && ch > 0) {
                doc.zones().add(new MapDoc.Zone(kind, cx, cy, cw, ch));
                reserve(cx, cy, cw, ch);
            }
        }

        void zonesAtEnds() {
            int h = Math.min(4, doc.h());
            int y = (doc.h() - h) / 2;
            zone(MapDoc.PLAYERS, 0, y, Math.min(2, doc.w()), h);
            zone(MapDoc.NPCS, doc.w() - Math.min(2, doc.w()), y, Math.min(2, doc.w()), h);
        }

        /** Россыпь по плотности, % */
        void scatter(String code, int x, int y, int w, int h, int percent) {
            for (int j = y; j < y + h; j++) {
                for (int i = x; i < x + w; i++) {
                    if (random.nextInt(100) < percent) {
                        stamp(code, i, j, random.nextInt(2));
                    }
                }
            }
        }

        /** Материал по коду, если он есть у системы, иначе материал инструмента */
        private String materialOr(String code, String tool) {
            return rules.material(code).isPresent() ? code : material(tool);
        }

        /** Местность прямоугольником; у системы такой нет — ничего */
        void fill(String terrain, int x, int y, int w, int h) {
            if (rules.material(terrain).isEmpty()) {
                return;
            }
            for (int j = y; j < y + h; j++) {
                for (int i = x; i < x + w; i++) {
                    if (doc.inside(i, j)) {
                        doc.cells().put(MapDoc.cellKey(i, j), terrain);
                    }
                }
            }
        }

        void label(int x, int y, String text) {
            if (doc.inside(x, y)) {
                doc.labels().add(new MapDoc.Label(x, y, text));
            }
        }

        /** Верхние грани клеток x1..x2 ряда y */
        void hLine(int x1, int x2, int y, String material) {
            for (int x = x1; x <= x2; x++) {
                wall(MapDoc.top(x, y), material);
            }
        }

        /** Левые грани клеток y1..y2 столбца x */
        void vLine(int x, int y1, int y2, String material) {
            for (int y = y1; y <= y2; y++) {
                wall(MapDoc.left(x, y), material);
            }
        }

        /** Дверь в верхней грани клетки; клетки по обе стороны остаются свободными */
        void doorTop(int x, int y, String material) {
            door(MapDoc.top(x, y), material);
            reserve(x, y - 1, 1, 2);
        }

        /** Дверь в левой грани клетки; клетки по обе стороны остаются свободными */
        void doorLeft(int x, int y, String material) {
            door(MapDoc.left(x, y), material);
            reserve(x - 1, y, 2, 1);
        }

        /** Предмет с вероятностью, % */
        void maybe(int percent, String code, int x, int y, int rot) {
            if (random.nextInt(100) < percent) {
                stamp(code, x, y, rot);
            }
        }

        /** Машины у бордюра вдоль ряда (horizontal) или столбца {@code line}, от {@code from} до {@code to} */
        void carsAlong(int from, int to, int line, boolean horizontal, int percent) {
            int at = from + between(0, 2);
            while (at + 4 <= to) {
                if (random.nextInt(100) < percent) {
                    if (horizontal) {
                        stamp("car", at, line, random.nextBoolean() ? 1 : 3);
                    } else {
                        stamp("car", line, at, random.nextBoolean() ? 0 : 2);
                    }
                }
                at += between(5, 8);
            }
        }

        /**
         * Перекрёсток двух улиц 140 × 100 м: магазин со складом и чёрным ходом в переулок, ломбард, кафе с кухней,
         * подъезд жилого дома, парковка с будкой, сквер с фонтаном. Игроки — с запада, NPC — с севера.
         */
        void crossroads() {
            String brick = material("room");
            String inner = material("wall");
            String door = material("door");
            String heavy = material("heavy_door");
            // проезжая часть — восемь клеток, тротуары — по две
            fill("asphalt", 0, 22, 70, 8);
            fill("asphalt", 32, 0, 8, 50);
            label(14, 25, "ул. Ржавая");
            label(36, 10, "пр. Корпоративный");
            zone(MapDoc.PLAYERS, 0, 24, 2, 4);
            zone(MapDoc.NPCS, 34, 0, 4, 2);

            // северо-запад: переулок, магазин, ломбард
            label(10, 1, "Переулок");
            stamp("dumpster", 6, 1, 0);
            stamp("dumpster", 15, 2, 0);
            stamp("dumpster", 25, 1, 0);
            scatter("crate", 0, 0, 30, 4, 5);
            room(2, 5, 18, 14, brick);
            for (int x = 3; x <= 18; x++) {
                window(MapDoc.top(x, 19));
            }
            doorTop(10, 19, door);
            doorTop(11, 19, door);
            hLine(2, 19, 9, inner);
            doorTop(17, 9, door);
            doorTop(4, 5, heavy);
            label(12, 7, "Склад магазина");
            scatter("crate", 6, 5, 14, 2, 30);
            label(8, 12, "Магазин 24/7");
            for (int y : new int[]{11, 14}) {
                for (int x : new int[]{3, 7, 11}) {
                    maybe(90, "shelf", x, y, 0);
                }
            }
            stamp("bar", 14, 16, 0);
            room(22, 8, 8, 11, brick);
            doorTop(25, 19, heavy);
            window(MapDoc.top(23, 19));
            window(MapDoc.top(27, 19));
            window(MapDoc.left(30, 12));
            window(MapDoc.left(30, 15));
            stamp("bar", 23, 12, 0);
            scatter("crate", 22, 8, 8, 3, 30);
            label(25, 15, "Ломбард");

            // северо-восток: двор, кафе, жилой дом
            label(48, 1, "Двор");
            stamp("dumpster", 44, 3, 0);
            stamp("dumpster", 49, 7, 0);
            maybe(70, "car", 52, 2, 1);
            room(42, 10, 14, 9, brick);
            for (int x = 43; x <= 54; x++) {
                window(MapDoc.top(x, 19));
            }
            doorTop(48, 19, door);
            doorTop(49, 19, door);
            window(MapDoc.left(42, 12));
            window(MapDoc.left(42, 14));
            doorLeft(42, 16, door);
            room(51, 10, 5, 4, inner);
            doorTop(52, 14, door);
            doorLeft(56, 11, heavy);
            label(53, 11, "Кухня");
            stamp("bar", 44, 11, 0);
            label(47, 13, "Кафе «Неон»");
            for (int[] at : new int[][]{{44, 14}, {47, 15}, {44, 17}, {51, 16}, {53, 17}, {43, 19}, {52, 19}}) {
                maybe(80, "desk", at[0], at[1], 0);
            }
            room(58, 2, 11, 17, brick);
            doorTop(63, 19, door);
            for (int x : new int[]{60, 61, 65, 66}) {
                window(MapDoc.top(x, 19));
            }
            for (int x : new int[]{59, 60, 61, 65, 66, 67}) {
                window(MapDoc.top(x, 2));
            }
            window(MapDoc.left(69, 5));
            window(MapDoc.left(69, 8));
            hLine(58, 68, 12, inner);
            vLine(63, 2, 11, inner);
            doorTop(60, 12, door);
            doorTop(66, 12, door);
            label(60, 5, "Кв. 12");
            label(66, 5, "Кв. 14");
            maybe(70, "desk", 59, 8, 0);
            maybe(70, "desk", 65, 8, 0);
            label(63, 15, "Подъезд");
            label(59, 13, "Лестница");
            stamp("mailbox", 68, 16, 0);
            stamp("mailbox", 68, 17, 0);

            // юго-запад: парковка с будкой
            room(25, 33, 3, 3, inner);
            doorTop(26, 36, door);
            window(MapDoc.left(25, 34));
            window(MapDoc.top(26, 33));
            label(14, 39, "Парковка");
            stamp("pole", 2, 38, 0);
            stamp("pole", 23, 39, 0);
            for (int y : new int[]{33, 41, 45}) {
                for (int x = 3; x <= 21; x += 3) {
                    maybe(70, "car", x, y, random.nextInt(4) < 3 ? 0 : 2);
                }
            }

            // юго-восток: сквер — газон, дорожки крестом, фонтан, деревья
            label(50, 37, "Сквер");
            for (int y = 34; y <= 47; y++) {
                for (int x = 44; x <= 67; x++) {
                    if (y != 40 && y != 41 && x != 55 && x != 56) {
                        fill("grass", x, y, 1, 1);
                    }
                }
            }
            fill("water", 54, 39, 4, 4);
            reserve(54, 39, 4, 4);
            reserve(44, 40, 24, 2);
            reserve(55, 34, 2, 14);
            for (int y = 34; y <= 47; y++) {
                for (int x = 44; x <= 67; x++) {
                    maybe(7, "tree", x, y, 0);
                }
            }
            stamp("dataterm", 43, 33, 0);

            // улица: фонари на углах, гидранты, почтовый ящик, DataTerm; машины у бордюров
            for (int[] at : new int[][]{{30, 20}, {41, 20}, {30, 31}, {41, 31}, {15, 21}, {55, 21}, {15, 30}, {55, 30}}) {
                stamp("pole", at[0], at[1], 0);
            }
            stamp("hydrant", 27, 21, 0);
            stamp("hydrant", 44, 30, 0);
            stamp("mailbox", 46, 21, 0);
            stamp("dataterm", 31, 12, 0);
            carsAlong(2, 30, 22, true, 75);
            carsAlong(2, 30, 28, true, 75);
            carsAlong(43, 70, 22, true, 75);
            carsAlong(43, 70, 28, true, 75);
            carsAlong(2, 21, 32, false, 75);
            carsAlong(31, 50, 38, false, 75);
            // брошенная машина посреди перекрёстка
            maybe(50, "car", 34, 25, 1);
        }

        /**
         * Грузовой порт 160 × 100 м: забор с КПП, проезд к причалу, лабиринт контейнеров, портальный кран, склад с воротами
         * и конторой, пирс в воду. Игроки — у ворот, NPC — в конторе склада.
         */
        void port() {
            String brick = material("room");
            String inner = material("wall");
            String door = material("door");
            String heavy = material("heavy_door");
            String fence = materialOr("cover_block", "room");
            fill("asphalt", 0, 0, 80, 4);
            label(40, 1, "Портовое шоссе");
            zone(MapDoc.PLAYERS, 8, 0, 6, 2);
            zone(MapDoc.NPCS, 71, 7, 4, 3);
            // бетонный забор, ворота шириной шесть клеток, проезд до причала
            hLine(0, 7, 4, fence);
            hLine(14, 79, 4, fence);
            fill("asphalt", 8, 4, 6, 33);
            reserve(8, 4, 6, 33);
            room(15, 5, 3, 3, brick);
            window(MapDoc.left(15, 6));
            doorTop(16, 8, door);
            label(16, 6, "КПП");

            // старые контейнеры вдоль забора на западе
            for (int x : new int[]{1, 4}) {
                for (int y = 6; y + 3 <= 36; y += 4) {
                    maybe(60, "container", x, y, 1);
                }
            }
            // контейнерная площадка: ряды по два с проходами, поперечный проезд посередине
            label(31, 8, "Контейнерная площадка");
            for (int y = 10; y <= 30; y += 4) {
                for (int x = 16; x + 3 <= 47; x += 3) {
                    if (x == 31) {
                        continue;
                    }
                    maybe(85, "container", x, y, 0);
                    maybe(85, "container", x, y + 1, 0);
                }
            }

            // причал: кран на рельсах, швартовые тумбы, вода и пирс
            label(31, 38, "Причал");
            label(23, 38, "Кран");
            for (int[] at : new int[][]{{18, 37}, {28, 37}, {18, 40}, {28, 40}}) {
                stamp("pole", at[0], at[1], 0);
            }
            maybe(70, "container", 21, 38, 0);
            maybe(50, "container", 24, 39, 0);
            for (int x = 2; x < 80; x += 8) {
                stamp("pole", x, 41, 0);
            }
            fill("water", 0, 42, 50, 8);
            fill("water", 56, 42, 24, 8);
            fill("water", 50, 48, 6, 2);
            reserve(0, 42, 50, 8);
            reserve(56, 42, 24, 8);
            label(20, 46, "Залив");
            label(52, 46, "Пирс");
            stamp("pole", 50, 47, 0);
            stamp("pole", 55, 47, 0);
            scatter("crate", 50, 42, 6, 4, 15);

            // склад: ворота к причалу и к проезду, контора в углу, стеллажи ящиков
            room(50, 6, 27, 22, brick);
            for (int x = 58; x <= 61; x++) {
                doorTop(x, 28, heavy);
            }
            doorLeft(50, 16, heavy);
            doorLeft(50, 17, heavy);
            doorTop(72, 28, door);
            for (int x = 53; x <= 65; x += 3) {
                window(MapDoc.top(x, 6));
            }
            room(70, 6, 7, 6, inner);
            doorTop(72, 12, door);
            window(MapDoc.top(74, 12));
            window(MapDoc.left(70, 9));
            label(73, 10, "Контора");
            maybe(80, "desk", 75, 10, 0);
            label(60, 7, "Склад №3");
            for (int y = 9; y <= 24; y += 3) {
                for (int x = 53; x <= 67; x++) {
                    maybe(70, "crate", x, y, 0);
                }
            }
            // площадка у склада: фуры, грузы
            maybe(80, "car", 54, 31, 1);
            maybe(60, "car", 66, 33, 1);
            maybe(60, "armored_car", 73, 30, 0);
            scatter("crate", 48, 29, 32, 8, 4);
        }

        /**
         * Этаж корпоративного офиса 120 × 80 м: стеклянный фасад с простенками, бетонное ядро с лестницами, лифтами
         * и серверной, переговорные, архив, охрана, приёмная и кабинет директора, открытые офисы, ресепшен у входа.
         * Игроки — на площади перед входом, NPC — у директора.
         */
        void office() {
            String outer = material("room");
            String inner = material("wall");
            String door = material("door");
            String heavy = material("heavy_door");
            String core = materialOr("cover_block", "room");
            fill("asphalt", 0, 38, 60, 2);
            zone(MapDoc.PLAYERS, 27, 38, 6, 2);
            zone(MapDoc.NPCS, 50, 3, 4, 2);
            // фасад: стекло, простенок через четыре клетки; вход — четыре двери
            room(1, 1, 58, 36, outer);
            for (int x = 1; x <= 58; x++) {
                if ((x - 1) % 4 != 0) {
                    window(MapDoc.top(x, 1));
                    window(MapDoc.top(x, 37));
                }
            }
            for (int y = 1; y <= 36; y++) {
                if ((y - 1) % 4 != 0) {
                    window(MapDoc.left(1, y));
                    window(MapDoc.left(59, y));
                }
            }
            for (int x = 28; x <= 31; x++) {
                doorTop(x, 37, door);
            }
            label(10, 38, "Площадь");

            // север: охрана, архив, переговорные, кухня, приёмная и кабинет директора; коридор вдоль них
            room(1, 1, 8, 9, inner);
            doorTop(5, 10, heavy);
            label(4, 3, "Охрана");
            stamp("desk", 3, 5, 0);
            stamp("dataterm", 7, 2, 0);
            room(9, 1, 13, 9, inner);
            doorTop(15, 10, door);
            label(15, 8, "Архив");
            for (int y : new int[]{3, 6}) {
                for (int x : new int[]{10, 14, 18}) {
                    maybe(90, "shelf", x, y, 0);
                }
            }
            for (int[] meeting : new int[][]{{22, 25}, {30, 33}}) {
                int x0 = meeting[0];
                room(x0, 1, 8, 9, inner);
                for (int x = x0 + 1; x <= x0 + 6; x++) {
                    window(MapDoc.top(x, 10));
                }
                doorTop(meeting[1], 10, door);
                label(x0 + 4, 2, "Переговорная");
                for (int[] at : new int[][]{{x0 + 2, 4}, {x0 + 4, 4}, {x0 + 2, 5}, {x0 + 4, 5}}) {
                    stamp("desk", at[0], at[1], 0);
                }
            }
            room(38, 1, 4, 9, inner);
            doorTop(39, 10, door);
            label(39, 5, "Кухня");
            stamp("bar", 38, 2, 0);
            room(42, 1, 17, 9, inner);
            doorTop(45, 10, door);
            vLine(48, 1, 9, inner);
            doorLeft(48, 6, door);
            label(45, 3, "Приёмная");
            maybe(90, "desk", 43, 5, 0);
            label(53, 2, "Директор");
            stamp("desk", 54, 6, 0);
            maybe(60, "desk", 50, 7, 0);

            // ядро: лестницы, туалеты, лифты, серверная; лифтовой холл открыт к югу
            room(24, 12, 12, 12, core);
            room(24, 12, 4, 5, core);
            doorLeft(24, 14, heavy);
            label(25, 14, "Лестница");
            room(32, 12, 4, 5, core);
            doorLeft(36, 14, heavy);
            label(34, 14, "Лестница");
            room(28, 12, 4, 5, inner);
            doorTop(29, 12, door);
            label(29, 14, "WC");
            for (int x : new int[]{25, 29, 33}) {
                room(x, 17, 2, 2, core);
                doorTop(x, 19, heavy);
            }
            label(31, 21, "Лифты");
            room(24, 20, 3, 4, core);
            doorLeft(27, 21, heavy);
            label(25, 20, "Серверная");
            for (int[] at : new int[][]{{24, 21}, {24, 22}, {24, 23}, {25, 23}}) {
                stamp("dataterm", at[0], at[1], 0);
            }
            doc.walls().remove(MapDoc.top(29, 24));
            doc.walls().remove(MapDoc.top(30, 24));
            reserve(29, 23, 2, 2);

            // открытые офисы: колонны, ряды столов, конференц-зал за стеклом
            for (int[] at : new int[][]{{11, 23}, {47, 21}, {11, 11}}) {
                stamp("pole", at[0], at[1], 0);
            }
            for (int y : new int[]{13, 16, 19, 27, 30, 33}) {
                for (int x = 3; x + 2 <= 21; x += 3) {
                    maybe(85, "desk", x, y, 0);
                }
            }
            room(48, 12, 11, 8, inner);
            for (int y = 13; y <= 18; y++) {
                window(MapDoc.left(48, y));
            }
            for (int x = 50; x <= 56; x++) {
                window(MapDoc.top(x, 20));
            }
            doorLeft(48, 15, door);
            label(53, 13, "Конференц-зал");
            for (int x = 51; x <= 55; x += 2) {
                stamp("desk", x, 15, 0);
                stamp("desk", x, 16, 0);
            }
            for (int y : new int[]{13, 16}) {
                for (int x = 40; x + 2 <= 47; x += 3) {
                    maybe(85, "desk", x, y, 0);
                }
            }
            for (int y : new int[]{23, 26, 29, 32, 35}) {
                for (int x = 40; x + 2 <= 58; x += 3) {
                    maybe(85, "desk", x, y, 0);
                }
            }

            // холл: стеклянные стены, ресепшен, растения в кадках
            for (int y = 26; y <= 36; y++) {
                window(MapDoc.left(22, y));
                window(MapDoc.left(38, y));
            }
            doorLeft(22, 30, door);
            doorLeft(38, 30, door);
            stamp("bar", 28, 29, 0);
            label(30, 27, "Ресепшен");
            for (int[] at : new int[][]{{23, 27}, {36, 27}, {23, 35}, {36, 35}}) {
                stamp("tree", at[0], at[1], 0);
            }
        }

        /** Дорога посередине, ряд зданий сверху дверями к улице, машины вдоль бордюров, баки и столбы на тротуаре */
        void street() {
            int w = doc.w();
            int h = doc.h();
            int roadTop = h / 2 - 2;
            int roadBottom = roadTop + 5;
            zone(MapDoc.PLAYERS, 0, roadTop + 1, 2, 4);
            zone(MapDoc.NPCS, w - 2, roadTop + 1, 2, 4);
            // здания над тротуаром
            int buildingBottom = roadTop - 2;
            int x = 1;
            while (x < w - 4 && buildingBottom >= 3) {
                int bw = Math.min(between(5, 9), w - 1 - x);
                if (bw < 4) {
                    break;
                }
                int bh = between(3, buildingBottom);
                int by = buildingBottom - bh;
                room(x, by, bw, bh, material("room"));
                int doorX = x + between(1, bw - 2);
                door(MapDoc.top(doorX, buildingBottom), material("door"));
                reserve(doorX, buildingBottom, 1, 1);
                if (bw > 5) {
                    window(MapDoc.top(doorX == x + 1 ? x + bw - 2 : x + 1, buildingBottom));
                }
                x += bw + between(1, 2);
            }
            // машины у верхнего и нижнего края дороги, развёрнуты вдоль
            for (int cx = 2 + between(0, 2); cx < w - 6; cx += between(5, 9)) {
                stamp("car", cx, roadTop, 1);
            }
            for (int cx = 3 + between(0, 3); cx < w - 6; cx += between(6, 10)) {
                stamp("car", cx, roadBottom - 1, 1);
            }
            // тротуар снизу: баки, столбы, гидрант
            int sidewalk = Math.min(h - 1, roadBottom + 1);
            for (int cx = 1; cx < w - 2; cx += between(4, 7)) {
                String code = switch (random.nextInt(4)) {
                    case 0 -> "dumpster";
                    case 1 -> "pole";
                    case 2 -> "hydrant";
                    default -> "mailbox";
                };
                stamp(code, cx, sidewalk, 0);
            }
            stamp("pole", Math.max(0, buildingBottom + 1), buildingBottom + 1, 0);
        }

        /** Зал со стойкой, подсобка за дверью, вход с улицы, машина у входа */
        void bar() {
            int w = doc.w();
            int h = doc.h();
            int hallW = Math.max(6, w * 2 / 3);
            int hallH = Math.max(5, h - 6);
            room(1, 1, hallW, hallH, material("room"));
            int backW = Math.max(3, w - hallW - 3);
            int backH = Math.max(3, hallH / 2);
            room(1 + hallW, 1, backW, backH, material("room"));
            door(MapDoc.left(1 + hallW, 1 + backH / 2), material("door"));
            reserve(hallW, 1 + backH / 2, 2, 1);
            // чёрный ход — тяжёлая дверь
            door(MapDoc.top(1 + hallW + backW / 2, 1 + backH), material("heavy_door"));
            int entrance = 1 + hallW / 2;
            door(MapDoc.top(entrance, 1 + hallH), material("door"));
            reserve(entrance, hallH, 1, 2);
            window(MapDoc.top(Math.max(2, entrance - 3), 1 + hallH));
            window(MapDoc.top(Math.min(hallW, entrance + 3), 1 + hallH));
            stamp("bar", 2 + between(0, 2), 2, 0);
            scatter("crate", 2, 5, hallW - 2, hallH - 5, 6);
            scatter("crate", 2 + hallW, 2, backW - 1, backH - 1, 30);
            zone(MapDoc.PLAYERS, Math.max(0, entrance - 2), h - 2, 5, 2);
            zone(MapDoc.NPCS, 3, 3, 4, 2);
            stamp("car", Math.min(w - 5, entrance + 4), h - 3, 1);
        }

        /** Большой зал с ящиками рядами, контора в углу, ворота */
        void warehouse() {
            int w = doc.w();
            int h = doc.h();
            room(1, 1, w - 2, h - 2, material("room"));
            int officeW = Math.max(4, w / 5);
            int officeH = Math.max(3, h / 4);
            room(w - 1 - officeW, 1, officeW, officeH, material("wall"));
            door(MapDoc.top(w - 1 - officeW / 2 - 1, 1 + officeH), material("door"));
            reserve(w - 1 - officeW / 2 - 1, 1 + officeH, 1, 1);
            for (int i = 0; i < 3 && 2 + i < w - 2; i++) {
                door(MapDoc.top(w / 2 - 1 + i, h - 1), material("heavy_door"));
            }
            door(MapDoc.left(1, h / 2), material("door"));
            zone(MapDoc.PLAYERS, w / 2 - 2, h - 4, 5, 2);
            zone(MapDoc.NPCS, w - 1 - officeW + 1, 2, officeW - 2, officeH - 2);
            reserve(2, h / 2 - 1, 2, 3);
            // стеллажи ящиков рядами с проходами
            for (int y = 3; y < h - 5; y += 3) {
                for (int x = 3; x < w - officeW - 3; x++) {
                    if (random.nextInt(100) < 70) {
                        stamp("crate", x, y, 0);
                    }
                }
            }
        }

        /** Ряды машин с проездами, столбы освещения */
        void parking() {
            int w = doc.w();
            int h = doc.h();
            zone(MapDoc.PLAYERS, 0, h / 2 - 2, 2, 4);
            zone(MapDoc.NPCS, w - 2, h / 2 - 2, 2, 4);
            for (int y = 1; y + 4 <= h - 1; y += 7) {
                for (int x = 3; x + 2 <= w - 3; x += 3) {
                    if (random.nextInt(100) < 70) {
                        stamp("car", x, y, random.nextInt(4) < 3 ? 0 : 2);
                    }
                }
            }
            for (int x = 4; x < w - 3; x += between(6, 9)) {
                stamp("pole", x, Math.min(h - 1, 6), 0);
            }
        }

        /** Коридор посередине, комнаты с дверями по обе стороны */
        void corridors() {
            int w = doc.w();
            int h = doc.h();
            int top = h / 2 - 1;
            room(1, 1, w - 2, h - 2, material("room"));
            for (int side = 0; side < 2; side++) {
                int y = side == 0 ? 1 : top + 3;
                int rh = side == 0 ? top - 1 : h - 2 - (top + 2);
                if (rh < 2) {
                    continue;
                }
                int x = 1;
                while (x < w - 3) {
                    int rw = Math.min(between(4, 7), w - 1 - x);
                    room(x, y, rw, rh, material("wall"));
                    int doorX = x + between(1, Math.max(1, rw - 2));
                    door(MapDoc.top(doorX, side == 0 ? y + rh : y), material("door"));
                    if (random.nextInt(3) == 0) {
                        scatter("crate", x + 1, y + 1, rw - 2, rh - 2, 25);
                    }
                    x += rw;
                }
            }
            door(MapDoc.left(1, top + 1), material("heavy_door"));
            door(MapDoc.left(w - 1, top + 1), material("heavy_door"));
            zone(MapDoc.PLAYERS, 2, top, 2, 3);
            zone(MapDoc.NPCS, w - 4, top, 2, 3);
        }

        /** Крыши через узкие проулки: в парапетах проёмы, через них видно и перепрыгивают; на крышах вентиляция и антенны */
        void roofs() {
            int w = doc.w();
            int h = doc.h();
            int x = 0;
            int lastX = 0;
            int lastY = 0;
            while (x < w - 3) {
                int rw = Math.min(between(7, 11), w - x);
                int ry = between(0, 2);
                int rh = h - ry - between(0, 2);
                room(x, ry, rw, rh, material("room"));
                for (int k = 0; k < 2; k++) {
                    int gy = ry + between(1, Math.max(1, rh - 2));
                    doc.walls().put(MapDoc.left(x, gy), "gap");
                    doc.walls().put(MapDoc.left(Math.min(w, x + rw), gy), "gap");
                }
                scatter(random.nextBoolean() ? "dataterm" : "crate", x + 1, ry + 1, rw - 2, rh - 2, 8);
                if (x == 0) {
                    zone(MapDoc.PLAYERS, 1, ry + 1, 2, 3);
                }
                lastX = x;
                lastY = ry;
                x += rw + between(1, 2);
            }
            zone(MapDoc.NPCS, Math.max(lastX + 1, w - 4), lastY + 1, 2, 3);
        }
    }
}
