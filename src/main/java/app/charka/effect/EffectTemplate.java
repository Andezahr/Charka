package app.charka.effect;

/**
 * Готовый временный эффект системы: препарат, стимулятор. Список даёт {@code GameSystem.getEffectTemplates()}.
 *
 * @param code     код
 * @param name     название
 * @param stat     код стата, который правит эффект; null — без числа
 * @param modifier на сколько
 * @param duration срок: кубики ({@code 1d6+1}) или число
 * @param unit     единица срока
 * @param note     что ещё делает, словами
 */
public record EffectTemplate(String code, String name, String stat, int modifier, String duration, DurationUnit unit, String note) {

    /** Единица срока при вводе; минуты хранятся раундами */
    public enum DurationUnit {
        ROUND("раунды"), MINUTE("минуты"), HOUR("часы"), DAY("дни");

        private final String label;

        DurationUnit(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** «+1 INT · 1d6+1 мин» */
    public String getSummary() {
        String change = stat == null || modifier == 0 ? "" : (modifier > 0 ? "+" : "") + modifier + " " + stat.toUpperCase() + " · ";
        return change + duration + " " + switch (unit) {
            case ROUND -> "р.";
            case MINUTE -> "мин";
            case HOUR -> "ч";
            case DAY -> "дн.";
        };
    }
}
