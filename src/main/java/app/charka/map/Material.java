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
 */
public record Material(String code, String label, int sp, Layer layer, boolean blocksMove, boolean blocksSight) {

    public enum Layer { WALL, DOOR, WINDOW, OBJECT, TERRAIN }
}
