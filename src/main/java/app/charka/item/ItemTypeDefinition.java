package app.charka.item;

import app.charka.dice.DiceExpression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Тип предмета: какие поля у него есть сверх названия, количества и цены. Список типов даёт {@code GameSystem.getItemTypes()}.
 *
 * @param code        код, хранится в {@code Item.type}
 * @param displayName название для интерфейса
 * @param fields      поля в порядке показа
 * @param check       проверка значений вместе (когда одно поле зависит от другого); бросает {@link IllegalArgumentException}
 * @param stateKeys   свойства-состояние, которых нет в форме: правка предмета того же типа их сохраняет
 */
public record ItemTypeDefinition(String code, String displayName, List<ItemField> fields, Consumer<Map<String, String>> check,
                                 List<String> stateKeys) {

    public static final String GENERIC_CODE = "GENERIC";

    /** Обычный предмет без дополнительных полей, есть в любой системе */
    public static final ItemTypeDefinition GENERIC = new ItemTypeDefinition(GENERIC_CODE, "Обычный предмет", List.of());

    private static final int MAX_TEXT = 255;

    /**
     * Кусок строки предмета.
     *
     * @param text  что показать: «WA 0», «SMG»
     * @param title расшифровка: «Точность», «Тип: Пистолет-пулемёт»
     */
    public record Line(String text, String title) {
    }

    public ItemTypeDefinition {
        fields = List.copyOf(fields);
        stateKeys = List.copyOf(stateKeys);
    }

    public ItemTypeDefinition(String code, String displayName, List<ItemField> fields, Consumer<Map<String, String>> check) {
        this(code, displayName, fields, check, List.of());
    }

    public ItemTypeDefinition(String code, String displayName, List<ItemField> fields) {
        this(code, displayName, fields, values -> {
        });
    }

    /**
     * Проверяет ввод формы и приводит его к хранимому виду. Пустые значения и незнакомые ключи не сохраняются.
     *
     * @throws IllegalArgumentException если значение не подходит полю
     */
    public Map<String, String> normalize(Map<String, String> input) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ItemField field : fields) {
            String raw = input.get(field.key());
            String value = raw == null ? "" : raw.strip();
            if (value.isEmpty()) {
                if (field.required()) {
                    throw new IllegalArgumentException("Поле «" + field.label() + "» обязательно");
                }
                continue;
            }
            result.put(field.key(), switch (field.kind()) {
                case INT -> number(field, value);
                case TEXT -> text(field, value);
                case DICE -> dice(field, value);
                case ENUM -> choice(field, value);
                case FLAG -> "1";
            });
        }
        check.accept(result);
        return result;
    }

    /**
     * Строка предмета из сохранённых значений, в порядке полей.
     */
    public List<Line> describe(Map<String, String> properties) {
        List<Line> lines = new ArrayList<>();
        for (ItemField field : fields) {
            String value = properties.get(field.key());
            if (value == null) {
                continue;
            }
            if (field.isFlag()) {
                // пустая подпись: флаг и так виден по кнопке действия
                if (!field.prefix().isEmpty()) {
                    lines.add(new Line(field.prefix(), field.label()));
                }
            } else if (field.isChoice()) {
                lines.add(new Line(value, field.label() + ": " + optionLabel(field, value)));
            } else {
                lines.add(new Line(field.prefix() + value + field.suffix(), field.label()));
            }
        }
        return lines;
    }

    private static String number(ItemField field, String value) {
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Поле «" + field.label() + "» должно быть числом: " + value);
        }
        if ((field.min() != null && number < field.min()) || (field.max() != null && number > field.max())) {
            throw new IllegalArgumentException("Поле «" + field.label() + "» вне допустимых значений: " + value);
        }
        return String.valueOf(number);
    }

    private static String text(ItemField field, String value) {
        if (value.length() > MAX_TEXT) {
            throw new IllegalArgumentException("Поле «" + field.label() + "» длиннее " + MAX_TEXT + " символов");
        }
        return value;
    }

    private static String dice(ItemField field, String value) {
        try {
            return DiceExpression.parse(value).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Поле «" + field.label() + "» должно быть кубиками, например 2d6+1: " + value);
        }
    }

    private static String choice(ItemField field, String value) {
        boolean known = field.options().stream().anyMatch(option -> option.value().equals(value));
        if (!known) {
            throw new IllegalArgumentException("Поле «" + field.label() + "»: неизвестное значение " + value);
        }
        return value;
    }

    private static String optionLabel(ItemField field, String value) {
        return field.options().stream()
                .filter(option -> option.value().equals(value))
                .map(ItemField.Option::label)
                .findFirst()
                .orElse(value);
    }
}
