package app.charka.attack;

import java.util.List;
import java.util.Map;

/**
 * Урон по площади: брошен один раз на всю зону. Что значит для целей, решает система: у неё есть {@code data}.
 *
 * @param weapon     название для лога
 * @param expression запись броска; если урона нет — текст эффекта («Газ»)
 * @param amount     урон; null — это не урон, эффект применяет мастер
 * @param radius     зона поражения, м
 * @param data       данные системы для попадания: тип урона и т.п.; ядро их только хранит
 * @param steps      как определены зона и урон
 */
public record AreaDamage(String weapon, String expression, Integer amount, int radius, Map<String, String> data, List<String> steps) {

    public AreaDamage {
        data = Map.copyOf(data);
        steps = List.copyOf(steps);
    }
}
