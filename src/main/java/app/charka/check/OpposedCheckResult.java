package app.charka.check;

/**
 * Итог противопоставленной проверки: оба участника бросают по правилам системы, сложности нет.
 *
 * @param initiator кто начал проверку
 * @param opponent  против кого: при равенстве итогов он защищающийся
 * @param winner    кто победил
 * @param verdict   итог для заголовка и лога: «Победа: Олежа (19 против 17)»
 */
public record OpposedCheckResult(Side initiator, Side opponent, Winner winner, String verdict) {

    public enum Winner {
        INITIATOR, OPPONENT,
        /** Оба критически провалились */
        NONE
    }

    /**
     * @param name  имя участника
     * @param skill что проверялось, null — у участника только число (противник без листа)
     */
    public record Side(String name, String skill, CheckResult result) {

        /**
         * Подпись участника: «Олежа · Скрытность».
         */
        public String title() {
            return skill == null ? name : name + " · " + skill;
        }
    }
}
