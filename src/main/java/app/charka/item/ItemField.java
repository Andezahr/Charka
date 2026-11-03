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
 * @param options  варианты для {@link Kind#ENUM}; у {@link Kind#TEXT} — типичные значения, можно ввести и своё
 * @param advanced нужно редко: в форме свёрнуто
 * @param namePrefix выбор значения подставляет в пустое название предмета этот текст и значение: «Патроны 9mm»; пусто — не подставляет
 */
public record ItemField(String key, String label, Kind kind, boolean required, String prefix, String suffix,
                        Integer min, Integer max, List<Option> options, boolean advanced, String namePrefix) {

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

    public ItemField(String key, String label, Kind kind, boolean required, String prefix, String suffix,
                     Integer min, Integer max, List<Option> options, boolean advanced) {
        this(key, label, kind, required, prefix, suffix, min, max, options, advanced, "");
    }

    public static ItemField number(String key, String label, String prefix, String suffix, Integer min, Integer max) {
        return new ItemField(key, label, Kind.INT, false, prefix, suffix, min, max, List.of(), false);
    }

    public static ItemField text(String key, String label) {
        return new ItemField(key, label, Kind.TEXT, false, "", "", null, null, List.of(), false);
    }

    public static ItemField requiredText(String key, String label) {
        return new ItemField(key, label, Kind.TEXT, true, "", "", null, null, List.of(), false);
    }

    /**
     * Текст со списком типичных значений: выбрать быстрее и без опечаток, но своё значение тоже подходит.
     */
    public static ItemField suggested(String key, String label, boolean required, String namePrefix, List<Option> options) {
        return new ItemField(key, label, Kind.TEXT, required, "", "", null, null, options, false, namePrefix);
    }

    public static ItemField dice(String key, String label) {
        return new ItemField(key, label, Kind.DICE, false, "", "", null, null, List.of(), false);
    }

    public static ItemField choice(String key, String label, boolean required, Option... options) {
        return new ItemField(key, label, Kind.ENUM, required, "", "", null, null, List.of(options), false);
    }

    public static ItemField flag(String key, String label, String shown) {
        return new ItemField(key, label, Kind.FLAG, false, shown, "", null, null, List.of(), false);
    }

    public ItemField asAdvanced() {
        return new ItemField(key, label, kind, required, prefix, suffix, min, max, options, true, namePrefix);
    }

    public boolean isChoice() {
        return kind == Kind.ENUM;
    }

    public boolean isSuggested() {
        return kind == Kind.TEXT && !options.isEmpty();
    }

    public boolean isFlag() {
        return kind == Kind.FLAG;
    }

    public boolean isNumber() {
        return kind == Kind.INT;
    }
}
