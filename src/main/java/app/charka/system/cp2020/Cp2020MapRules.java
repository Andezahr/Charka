package app.charka.system.cp2020;

import app.charka.map.MapObject;
import app.charka.map.MapRules;
import app.charka.map.Material;
import app.charka.skill.StatValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Карта CP2020. Материалы — таблица укрытий {@link Cp2020Cover} (с. 103), одна на карту и диалог атаки.
 * HOMEBREW: сетки в книге нет (FNFF меряет метрами) — клетка 2 м, расстояние по прямой между центрами клеток.
 */
final class Cp2020MapRules implements MapRules {

    static final int CELL_METERS = 2;

    private static final Set<String> DOORS = Set.of("cover_wood_door", "cover_heavy_door", "cover_steel_door");
    private static final Set<String> WALLS = Set.of("cover_sheetrock", "cover_block", "cover_brick", "cover_stone");
    /** Низкое укрытие: за ним прячутся, но не стоят на нём */
    private static final Set<String> PASSABLE = Set.of("cover_curb");
    /**
     * HOMEBREW: высокие предметы закрывают взгляд, остальные (машина, ящик, стойка) ниже роста — поверх видно и стреляют.
     * Высоты в книге нет; цель за столбом видна частично и укрыта им.
     */
    private static final Set<String> TALL = Set.of("cover_tree", "cover_concrete_pole", "cover_dataterm", "cover_armored_car", "cover_av4");

    private static final List<Material> MATERIALS = buildMaterials();

    private static final List<MapObject> OBJECTS = List.of(
            // моторный конец сверху: двигатель держит больше пуль (с. 103)
            new MapObject("car", "Машина", 2, 4, List.of(
                    "cover_engine", "cover_engine",
                    "cover_car", "cover_car",
                    "cover_car", "cover_car",
                    "cover_car", "cover_car")),
            MapObject.solid("armored_car", "Бронемашина", 2, 4, "cover_armored_car"),
            MapObject.solid("crate", "Ящик", 1, 1, "cover_crate"),
            MapObject.solid("dumpster", "Мусорный бак", 2, 1, "cover_dumpster"),
            MapObject.solid("pole", "Столб", 1, 1, "cover_concrete_pole"),
            MapObject.solid("tree", "Дерево", 1, 1, "cover_tree"),
            MapObject.solid("dataterm", "DataTerm", 1, 1, "cover_dataterm"),
            MapObject.solid("mailbox", "Почтовый ящик", 1, 1, "cover_mailbox"),
            MapObject.solid("hydrant", "Гидрант", 1, 1, "cover_hydrant"),
            MapObject.solid("bar", "Стойка бара", 4, 1, "cover_bar"));

    private static List<Material> buildMaterials() {
        List<Material> materials = new ArrayList<>();
        for (String[] row : Cp2020Cover.rows()) {
            String code = row[0];
            Material.Layer layer = DOORS.contains(code) ? Material.Layer.DOOR
                    : WALLS.contains(code) ? Material.Layer.WALL : Material.Layer.OBJECT;
            boolean blocksMove = !PASSABLE.contains(code);
            // сквозь машину и ящик не пройти, но поверх видно и стреляют
            boolean blocksSight = layer != Material.Layer.OBJECT || TALL.contains(code);
            materials.add(new Material(code, row[1], Integer.parseInt(row[2]), layer, blocksMove, blocksSight));
        }
        // HOMEBREW: стекла в таблице укрытий нет — окно не укрывает, но не пускает
        materials.add(new Material("glass", "Стекло", 0, Material.Layer.WINDOW, true, false));
        materials.add(new Material("debris", "Обломки", 0, Material.Layer.TERRAIN, false, false));
        materials.add(new Material("water", "Вода", 0, Material.Layer.TERRAIN, false, false));
        return List.copyOf(materials);
    }

    @Override
    public int cellMeters() {
        return CELL_METERS;
    }

    @Override
    public List<Material> materials() {
        return MATERIALS;
    }

    @Override
    public List<MapObject> objects() {
        return OBJECTS;
    }

    @Override
    public Map<String, String> defaults() {
        return Map.of("room", "cover_brick", "wall", "cover_sheetrock", "door", "cover_wood_door",
                "heavy_door", "cover_steel_door", "window", "glass");
    }

    /** С. 103: «go ahead and shoot through the door; the penalties for blind firing won't stop you» */
    @Override
    public String blindFireOption() {
        return Cp2020AttackModifiers.BLIND_FIRE;
    }

    /** Бег MA × 3 м за действие (с. 98) */
    @Override
    public Integer reach(Map<String, StatValue> stats) {
        StatValue ma = stats.get(Cp2020StatsStrategy.MA);
        return ma == null ? null : Cp2020Rules.run(ma.value());
    }
}
