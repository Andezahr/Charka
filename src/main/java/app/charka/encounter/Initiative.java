package app.charka.encounter;

/**
 * Инициатива участника боя.
 *
 * @param total   чем больше, тем раньше ход
 * @param details как она получена: показывается подсказкой
 */
public record Initiative(int total, String details) {
}
