package app.charka.item;

import java.util.List;

/**
 * Поле предмета, которое задаёт его тип. Значения хранятся строками в {@code Item.properties}.
 *
 * @param key      ключ в {@code Item.properties}
 * @param label    подпись в форме
 * @param kind     что можно ввести
 * @param required без значения предмет не сохраняется
 * @param prefix   текст перед значением в строке предмета: «WA », «ROF »
 * @param suffix   текст после значения в строке предмета: « м»
 * @param min      нижняя граница числа, null — без неё
 * @param max      верхняя граница числа, null — без неё
 * @param options  варианты для {@link Kind#ENUM}
 */
public record ItemField(String key, String label, Kind kind, boolean required, String prefix, String suffix,
                        Integer min, Integer max, List<Option> options) {

    public enum Kind {
        INT, TEXT, DICE, ENUM, FLAG
    }

    /**
     * Значение и его расшифровка: «SMG» — «Пистолет-пулемёт».
     */
    public record Option(String value, String label) {
    }

    public ItemField {
        options = List.copyOf(options);
    }

    public static ItemField number(String key, String label, String prefix, String suffix, Integer min, Integer max) {
        return new ItemField(key, label, Kind.INT, false, prefix, suffix, min, max, List.of());
    }

    public static ItemField text(String key, String label) {
        return new ItemField(key, label, Kind.TEXT, false, "", "", null, null, List.of());
    }

    public static ItemField dice(String key, String label) {
        return new ItemField(key, label, Kind.DICE, false, "", "", null, null, List.of());
    }

    public static ItemField choice(String key, String label, boolean required, Option... options) {
        return new ItemField(key, label, Kind.ENUM, required, "", "", null, null, List.of(options));
    }

    public static ItemField flag(String key, String label, String shown) {
        return new ItemField(key, label, Kind.FLAG, false, shown, "", null, null, List.of());
    }

    public boolean isChoice() {
        return kind == Kind.ENUM;
    }

    public boolean isFlag() {
        return kind == Kind.FLAG;
    }

    public boolean isNumber() {
        return kind == Kind.INT;
    }
}
