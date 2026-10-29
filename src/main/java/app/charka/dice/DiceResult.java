package app.charka.dice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Результат броска: выпавшие кубики и сумма с модификатором.
 */
public record DiceResult(DiceExpression expression, List<Integer> rolls, int total) {

    public static DiceResult of(DiceExpression expression, List<Integer> rolls) {
        int sum = rolls.stream().mapToInt(Integer::intValue).sum();
        return new DiceResult(expression, List.copyOf(rolls), sum + expression.modifier());
    }

    /**
     * Расшифровка для лога и интерфейса: {@code [3, 5] + 2 = 10}, для одного кубика без модификатора — {@code [7] = 7}.
     */
    public String details() {
        String dice = rolls.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
        int modifier = expression.modifier();
        String mod = modifier > 0 ? " + " + modifier : modifier < 0 ? " - " + (-modifier) : "";
        return dice + mod + " = " + total;
    }
}
