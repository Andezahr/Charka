package app.charka.system.cp2020;

import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Кубик проверок CP2020 (книга, с. 42): 1d10; на 10 бросается один дополнительный кубик и прибавляется,
 * 1 на первом кубике — критический провал (фамбл).
 */
public final class Cp2020Dice {

    private Cp2020Dice() {
    }

    /**
     * @param rolls все выпавшие кубики по порядку
     * @param total их сумма
     */
    public record Roll(List<Integer> rolls, int total) {

        public Roll {
            rolls = List.copyOf(rolls);
        }

        public boolean fumble() {
            return rolls.getFirst() == 1;
        }

        public boolean exploded() {
            return rolls.size() > 1;
        }

        /**
         * «10 → +7 = 17», без взрыва — просто число.
         */
        public String describe() {
            if (!exploded()) {
                return String.valueOf(total);
            }
            return rolls.getFirst() + rolls.stream().skip(1).map(r -> " → +" + r).collect(Collectors.joining())
                    + " = " + total;
        }
    }

    public static Roll rollCheck(DiceRoller dice) {
        List<Integer> rolls = new ArrayList<>();
        int roll = dice.d10();
        rolls.add(roll);
        if (roll == 10) {
            rolls.add(dice.d10());
        }
        return new Roll(rolls, rolls.stream().mapToInt(Integer::intValue).sum());
    }
}
