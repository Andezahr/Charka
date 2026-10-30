package app.charka.attack;

import app.charka.item.ItemField.Option;

import java.util.List;

/**
 * Что нужно спросить у игрока для атаки этим оружием; модификаторы показываются сразу, а не свёрнуты.
 *
 * @param damage      урон оружия для заголовка диалога
 * @param range       дальность, м; null — не указана
 * @param unavailable почему сейчас нельзя атаковать; null — можно
 * @param skills      навык атаки; если вариант один, выбирать нечего
 * @param locations   зоны для прицельного выстрела
 * @param sections    модификаторы по разделам
 */
public record AttackForm(String damage, Integer range, String unavailable, List<Option> skills,
                         List<Option> locations, List<Section> sections) {

    /**
     * @param name    название раздела
     * @param choices выборы раздела
     */
    public record Section(String name, List<Choice> choices) {
    }

    /**
     * Один вариант — флажок; несколько — выбор одного из них или ни одного.
     *
     * @param key     имя выбора
     * @param label   подпись выбора из нескольких вариантов
     * @param options варианты: значение — код, подпись — текст со значением
     */
    public record Choice(String key, String label, List<Option> options) {

        public boolean isFlag() {
            return options.size() == 1;
        }
    }
}
