package app.charka.map;

import app.charka.skill.StatValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Правила карты системы: {@code GameSystem.getMapRules()}. Масштаб, мера расстояния, материалы, готовые предметы, досягаемость.
 */
public interface MapRules {

    /** Мера «прямая»: расстояние между центрами клеток в метрах, округлённое; формула повторяется в app.js */
    String STRAIGHT = "straight";

    /** Клетка по умолчанию, м */
    int cellMeters();

    /** Код меры для браузера: {@link #STRAIGHT} */
    default String metric() {
        return STRAIGHT;
    }

    /**
     * Расстояние между клетками, м.
     */
    default int distance(int cell, int x1, int y1, int x2, int y2) {
        return (int) Math.round(Math.hypot(x2 - x1, y2 - y1) * cell);
    }

    List<Material> materials();

    default Optional<Material> material(String code) {
        return materials().stream().filter(material -> material.code().equals(code)).findFirst();
    }

    List<MapObject> objects();

    default Optional<MapObject> object(String code) {
        return objects().stream().filter(object -> object.code().equals(code)).findFirst();
    }

    /**
     * Материал по умолчанию у инструмента редактора: {@code room}, {@code wall}, {@code door}, {@code heavy_door}, {@code window}.
     */
    Map<String, String> defaults();

    /**
     * Сколько метров персонаж пробегает за действие.
     *
     * @param stats текущие статы
     * @return null — система не знает
     */
    default Integer reach(Map<String, StatValue> stats) {
        return null;
    }

    /**
     * Модификатор атаки «стрельба вслепую»: цель не видна, но её закрывает одна преграда с прочностью — стреляют сквозь неё.
     *
     * @return код варианта модификатора; null — система так не стреляет, невидимая цель недоступна
     */
    default String blindFireOption() {
        return null;
    }

    /** Без системы: прямая в метрах, материалы без прочности */
    MapRules GENERIC = new MapRules() {

        private final List<Material> materials = List.of(
                new Material("wall", "Стена", 0, Material.Layer.WALL, true, true),
                new Material("partition", "Перегородка", 0, Material.Layer.WALL, true, true),
                new Material("door", "Дверь", 0, Material.Layer.DOOR, true, true),
                new Material("window", "Окно", 0, Material.Layer.WINDOW, true, false),
                new Material("crate", "Ящик", 0, Material.Layer.OBJECT, true, false),
                new Material("car", "Машина", 0, Material.Layer.OBJECT, true, false),
                new Material("desk", "Стол", 0, Material.Layer.OBJECT, true, false),
                new Material("shelf", "Стеллаж", 0, Material.Layer.OBJECT, true, true),
                new Material("container", "Контейнер", 0, Material.Layer.OBJECT, true, true),
                new Material("debris", "Обломки", 0, Material.Layer.TERRAIN, false, false),
                new Material("water", "Вода", 0, Material.Layer.TERRAIN, false, false),
                new Material("asphalt", "Асфальт", 0, Material.Layer.TERRAIN, false, false),
                new Material("grass", "Газон", 0, Material.Layer.TERRAIN, false, false));

        private final List<MapObject> objects = List.of(
                MapObject.solid("car", "Машина", 2, 4, "car"),
                MapObject.solid("crate", "Ящик", 1, 1, "crate"),
                MapObject.solid("desk", "Стол", 2, 1, "desk"),
                MapObject.solid("shelf", "Стеллаж", 3, 1, "shelf"),
                MapObject.solid("container", "Контейнер", 3, 1, "container"));

        @Override
        public int cellMeters() {
            return 2;
        }

        @Override
        public List<Material> materials() {
            return materials;
        }

        @Override
        public List<MapObject> objects() {
            return objects;
        }

        @Override
        public Map<String, String> defaults() {
            return Map.of("room", "wall", "wall", "partition", "door", "door", "heavy_door", "door", "window", "window");
        }
    };
}
