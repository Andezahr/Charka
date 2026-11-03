package app.charka.map;

import java.util.List;

/**
 * Готовый предмет для карты: машина, ящик, стойка бара. Занимает прямоугольник клеток, у каждой свой материал.
 *
 * @param code  код
 * @param label название
 * @param w     ширина без поворота
 * @param h     высота без поворота
 * @param cells материалы клеток по строкам сверху вниз, {@code w × h}; null — клетка свободна
 */
public record MapObject(String code, String label, int w, int h, List<String> cells) {

    public MapObject {
        if (cells.size() != w * h) {
            throw new IllegalArgumentException("У предмета " + code + " клеток должно быть " + w * h);
        }
        cells = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(cells));
    }

    /** Предмет из одного материала */
    public static MapObject solid(String code, String label, int w, int h, String material) {
        return new MapObject(code, label, w, h, java.util.Collections.nCopies(w * h, material));
    }

    /**
     * Материал клетки предмета, повёрнутого на {@code rot} четвертей по часовой.
     *
     * @param dx смещение от левого верхнего угла повёрнутого предмета
     */
    public String cellAt(int rot, int dx, int dy) {
        int r = Math.floorMod(rot, 4);
        int sx;
        int sy;
        switch (r) {
            case 1 -> { sx = dy; sy = h - 1 - dx; }
            case 2 -> { sx = w - 1 - dx; sy = h - 1 - dy; }
            case 3 -> { sx = w - 1 - dy; sy = dx; }
            default -> { sx = dx; sy = dy; }
        }
        return cells.get(sy * w + sx);
    }

    public int width(int rot) {
        return Math.floorMod(rot, 2) == 0 ? w : h;
    }

    public int height(int rot) {
        return Math.floorMod(rot, 2) == 0 ? h : w;
    }
}
