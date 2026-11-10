package app.charka.map;

/**
 * Из чего стена, дверь, предмет или местность.
 *
 * @param code        код; у CP2020 совпадает с кодом укрытия диалога атаки
 * @param label       название
 * @param sp          прочность укрытия; 0 — системе неважно
 * @param layer       где применяется в редакторе
 * @param blocksMove  клетку с ним не пройти (у граней решает вид грани)
 * @param blocksSight сквозь клетку с ним не видно
 * @param difficult   M-23: трудная местность — бег по ней дороже ({@link MapRules#difficultFactor})
 * @param smoke       M-23: дым — закрывает взгляд, но не пули: сквозь него стреляют вслепую
 * @param explosive   M-23: рвётся от попадания или взрыва рядом ({@link MapRules#explosive})
 */
public record Material(String code, String label, int sp, Layer layer, boolean blocksMove, boolean blocksSight,
                       boolean difficult, boolean smoke, boolean explosive) {

    public enum Layer { WALL, DOOR, WINDOW, OBJECT, TERRAIN }

    public Material(String code, String label, int sp, Layer layer, boolean blocksMove, boolean blocksSight) {
        this(code, label, sp, layer, blocksMove, blocksSight, false, false, false);
    }

    /** Трудная местность: обломки, вода */
    public static Material difficult(String code, String label) {
        return new Material(code, label, 0, Layer.TERRAIN, false, false, true, false, false);
    }

    /** M-22: пустота — пола нет, видно нижний уровень; встать нельзя */
    public static Material voidCell() {
        return new Material(LevelMap.VOID, "Пустота (видно вниз)", 0, Layer.TERRAIN, true, false);
    }

    /** M-22: лестница — на соседнем уровне в той же клетке тоже лестница */
    public static Material stairs() {
        return new Material(LevelMap.STAIRS, "Лестница", 0, Layer.TERRAIN, false, false);
    }

    /** Взрывоопасный предмет: не пройти, поверх видно */
    public static Material explosive(String code, String label, int sp) {
        return new Material(code, label, sp, Layer.OBJECT, true, false, false, false, true);
    }

    /** Дым: проходим, закрывает взгляд */
    public static Material smoke(String code, String label) {
        return new Material(code, label, 0, Layer.TERRAIN, false, true, false, true, false);
    }
}
