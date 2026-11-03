package app.charka.encounter;

/**
 * Что участник может объявить в начале раунда и что меняет его инициативу.
 *
 * @param code         код
 * @param label        подпись кнопки и отметки у участника
 * @param bonus        прибавка к инициативе
 * @param attackOption какой модификатор атаки сразу отметить; null — никакого
 * @param rounds       сколько следующих раундов действует; 0 — только этот, сразу и с очередью
 */
public record InitiativeDeclaration(String code, String label, int bonus, String attackOption, int rounds) {

    public InitiativeDeclaration(String code, String label, int bonus, String attackOption) {
        this(code, label, bonus, attackOption, 0);
    }
}
