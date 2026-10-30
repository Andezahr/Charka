package app.charka.attack;

import java.util.List;

/**
 * Урон по площади: брошен один раз на всю зону, зона попадания у каждой цели своя.
 * Если заряд не попал в цель, те же значения возвращаются из формы «Кого задело»: куда он упал, решает мастер.
 *
 * @param weapon     название оружия для лога
 * @param expression запись броска; если урона нет — текст эффекта («Газ»)
 * @param amount     урон; null — это не урон, эффект применяет мастер
 * @param damageType код типа урона системы
 * @param wholeBody  по всему телу, а не по зоне
 * @param armorWear  сколько SP снимает попадание
 * @param radius     зона поражения, м
 * @param steps      как определены зона и урон
 */
public record AreaDamage(String weapon, String expression, Integer amount, String damageType, boolean wholeBody,
                         int armorWear, int radius, List<String> steps) {

    public AreaDamage {
        steps = List.copyOf(steps);
    }
}
