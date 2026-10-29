package app.charka.dice;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Выражение броска {@code NdM+K}: N кубиков по M граней плюс модификатор K.
 *
 * @param count    число кубиков (N, по умолчанию 1)
 * @param sides    число граней (M)
 * @param modifier модификатор (K, может быть отрицательным)
 */
public record DiceExpression(int count, int sides, int modifier) {

    public static final int MAX_COUNT = 100;
    public static final int MAX_SIDES = 1000;
    public static final int MAX_MODIFIER = 10_000;

    /** «d» или русская «к» (2к6) */
    private static final Pattern PATTERN = Pattern.compile("^(\\d*)[dк](\\d+)([+-]\\d+)?$");

    public DiceExpression {
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("Число кубиков должно быть от 1 до " + MAX_COUNT);
        }
        if (sides < 2 || sides > MAX_SIDES) {
            throw new IllegalArgumentException("Число граней должно быть от 2 до " + MAX_SIDES);
        }
        if (Math.abs(modifier) > MAX_MODIFIER) {
            throw new IllegalArgumentException("Модификатор не может быть больше " + MAX_MODIFIER + " по модулю");
        }
    }

    /**
     * Разбирает {@code NdM}, {@code NdM+K}, {@code NdM-K}, {@code dM}; регистр не важен, пробелы игнорируются.
     *
     * @throws IllegalArgumentException на любой другой ввод
     */
    public static DiceExpression parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Пустое выражение броска");
        }
        String normalized = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        Matcher m = PATTERN.matcher(normalized);
        if (!m.matches()) {
            throw new IllegalArgumentException("Не понимаю бросок «" + text.strip() + "». Пример: 2d6+3, d20, 3d6-1");
        }
        try {
            int count = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
            int sides = Integer.parseInt(m.group(2));
            int modifier = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            return new DiceExpression(count, sides, modifier);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Слишком большое число в броске «" + text.strip() + "»");
        }
    }

    /**
     * Каноническая запись: {@code d20}, {@code 2d6+3}, {@code 3d6-1}.
     */
    @Override
    public String toString() {
        String dice = (count == 1 ? "" : String.valueOf(count)) + "d" + sides;
        if (modifier > 0) {
            return dice + "+" + modifier;
        }
        return modifier < 0 ? dice + modifier : dice;
    }
}
