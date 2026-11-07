package app.charka.system;

import java.util.List;

/**
 * K-11: поле листа персонажа, которое объявляет система и хранит {@code Character.profile}: теги, приговор, качества.
 *
 * @param key         ключ в профиле
 * @param label       подпись
 * @param hint        подсказка под полем; null — нет
 * @param suggestions типичные значения: список подсказок у поля или варианты выбора
 * @param multi       выбор нескольких из {@code suggestions} (хранится через запятую); иначе — текст
 * @param long_       длинный текст (предыстория)
 * @param brief       показывать в коротком описании персонажа в списке кампании
 * @param required    значения, которые выбраны всегда, когда поле пусто (обязательные качества)
 */
public record ProfileField(String key, String label, String hint, List<String> suggestions, boolean multi, boolean long_,
                           boolean brief, List<String> required) {

    public ProfileField {
        suggestions = List.copyOf(suggestions);
        required = List.copyOf(required);
    }

    public static ProfileField text(String key, String label, String hint, List<String> suggestions) {
        return new ProfileField(key, label, hint, suggestions, false, false, false, List.of());
    }

    public static ProfileField longText(String key, String label, String hint) {
        return new ProfileField(key, label, hint, List.of(), false, true, false, List.of());
    }

    public static ProfileField multi(String key, String label, String hint, List<String> options, List<String> required) {
        return new ProfileField(key, label, hint, options, true, false, false, required);
    }

    public boolean isLong() {
        return long_;
    }
}
