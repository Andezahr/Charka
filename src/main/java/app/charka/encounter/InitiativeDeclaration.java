package app.charka.encounter;

/**
 * Что участник может объявить в начале раунда и что меняет его инициативу.
 *
 * @param code         код
 * @param label        подпись кнопки и отметки у участника
 * @param bonus        прибавка к инициативе
 * @param attackOption какой модификатор атаки сразу отметить; null — никакого
 */
public record InitiativeDeclaration(String code, String label, int bonus, String attackOption) {
}
