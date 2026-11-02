package app.charka.map;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Генератор карт (M-08): улица, бар, склад, парковка, коридоры, крыши, пустая. Раскладка — ядро, материалы и предметы
 * берутся из {@link MapRules} системы; предмета, которого у системы нет, на карте просто не будет. Одно зерно — одна карта.
 */
@Component
public class MapGenerator {

    /**
     * @param code   код шаблона
     * @param label  название
     * @param width  размер по умолчанию, клеток
     * @param height размер по умолчанию, клеток
     */
    public record Template(String code, String label, int width, int height) {
    }

    public static final List<Template> TEMPLATES = List.of(
            new Template("street", "Улица", 30, 18),
            new Template("bar", "Бар", 24, 18),
            new Template("warehouse", "Склад", 28, 20),
            new Template("parking", "Парковка", 26, 18),
            new Template("corridors", "Коридоры", 28, 20),
            new Template("roofs", "Крыши", 30, 20),
            new Template("empty", "Пустая", 30, 20));

    public static Template template(String code) {
        return TEMPLATES.stream().filter(template -> template.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет такого шаблона карты: " + code));
    }

    /**
     * @throws IllegalArgumentException если шаблона нет или размер вне пределов
     */
    public MapDoc generate(String code, int width, int height, long seed, MapRules rules) {
        template(code);
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
