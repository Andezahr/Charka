package app.charka.system.kosmozeki;

import app.charka.model.Character;
import app.charka.system.ProfileField;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Лист персонажа Космозэков (K-11, раздел 8 {@code RULES_KOSMOZEKI.md}): три тега, приговор, качества, предыстория.
 * Механически теги ни на что не влияют.
 */
public final class KzProfile {

    private KzProfile() {
    }

    public static final String ADJECTIVE = "adjective";
    public static final String NOUN = "noun";
    public static final String VERB = "verb";
    public static final String SENTENCE = "sentence";
    public static final String QUALITIES = "qualities";
    public static final String BACKSTORY = "backstory";

    static final List<String> REQUIRED_QUALITIES = List.of("Инициативность", "Командный дух");

    public static final List<ProfileField> FIELDS = List.of(
            ProfileField.text(ADJECTIVE, "Прилагательное", "Самое яркое качество или умение",
                    List.of("атлетичный", "быстрый", "чуткий", "бдительный", "убедительный", "красивый", "энергичный", "выносливый",
                            "крепкий", "смышлёный", "начитанный", "меткий", "расчётливый", "в доску свой", "рукастый")),
            ProfileField.text(NOUN, "Существительное", "Суть: профессия, положение",
                    List.of("солдат", "убийца", "вор", "аристократ", "браконьер", "пилот", "врач", "пастор", "дипломат", "слуга",
                            "акробат", "механик", "нейробиолог", "археолог", "писатель", "радиоведущий", "клерк")),
            ProfileField.text(VERB, "Глагол", "Что хорошо делает или любит, кроме специализации",
                    List.of("поёт песни", "увлекается политикой", "читает журналы по электронике", "ест за двоих",
                            "лазает по скалам", "обучается новому", "подкатывает к каждому")),
            ProfileField.text(SENTENCE, "Приговор", "За что сел", List.of()),
            ProfileField.multi(QUALITIES, "Положительные качества", "Инициативность и командный дух обязательны",
                    List.of("Доброта", "Честность", "Надёжность", "Верность", "Оптимизм", "Скромность", "Смелость", "Терпеливость",
                            "Щедрость", "Инициативность", "Чувство юмора", "Командный дух", "Бесконфликтность", "Любознательность",
                            "Трудолюбие", "Самокритичность", "Амбициозность", "Благодарность", "Жизнелюбие"),
                    REQUIRED_QUALITIES),
            ProfileField.longText(BACKSTORY, "Предыстория", "Необязательна"));

    /** «Меткий солдат, который поёт песни»; null — тегов нет */
    public static String tags(Character character) {
        if (character.getProfile() == null) {
            return null;
        }
        String adjective = value(character, ADJECTIVE);
        String noun = value(character, NOUN);
        String verb = value(character, VERB);
        String head = Stream.of(adjective, noun).filter(part -> part != null).collect(Collectors.joining(" "));
        if (head.isEmpty() && verb == null) {
            return null;
        }
        String phrase = head.isEmpty() ? verb : verb == null ? head : head + ", который " + verb;
        return phrase.substring(0, 1).toUpperCase() + phrase.substring(1);
    }

    private static String value(Character character, String key) {
        String value = character.getProfile().get(key);
        return value == null || value.isBlank() ? null : value.strip();
    }
}
