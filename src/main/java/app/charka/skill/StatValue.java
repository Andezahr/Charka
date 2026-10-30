package app.charka.skill;

/**
 * Стат персонажа для расчёта навыков.
 *
 * @param code  код стата
 * @param name  название для интерфейса
 * @param base  значение без штрафов
 * @param value текущее значение (с учётом ран и т.п.)
 */
public record StatValue(String code, String name, int base, int value) {

    public boolean isReduced() {
        return value < base;
    }
}
