package app.charka.encounter;

/**
 * Противостояние двух участников боя, которое предлагает система: оба бросают, у проигравшего остаётся эффект.
 * CP2020: Facedown (с. 55).
 *
 * @param code  код для адреса и для {@code GameSystem.getContestRequest}
 * @param label подпись кнопки
 */
public record CombatContest(String code, String label) {
}
