package app.charka.attack;

import app.charka.item.ItemField.Option;

import java.util.List;

/**
 * Что нужно спросить у игрока для атаки этим оружием; модификаторы показываются сразу, а не свёрнуты.
 *
 * @param damage      урон оружия для заголовка диалога
 * @param range       дальность, м; null — не указана
 * @param unavailable почему сейчас нельзя атаковать; null — можно
 * @param modes       режимы огня; если вариант один, выбирать нечего
 * @param maxRounds   сколько можно выпустить или израсходовать за раз
 * @param defaultRounds сколько предложить сразу
 * @param roundsLabel что считает это число: патроны, пятна, единицы взрывчатки
 * @param skills      навык атаки; если вариант один, выбирать нечего
 * @param locations   зоны для прицельного выстрела
 * @param sections    модификаторы по разделам
 */
public record AttackForm(String damage, Integer range, String unavailable, List<Mode> modes, int maxRounds, int defaultRounds, String roundsLabel, List<Option> skills,
                         List<Option> locations, List<Section> sections) {

    /**
     * @param code    код режима
     * @param label   подпись
     * @param rounds  режим спрашивает число патронов или единиц
     * @param zone    режим ведётся по зоне: цель не одна, спрашивается ширина
     * @param area    урон по площади: цель не одна, зона попадания случайная
     * @param planted заряд уже на месте: нет броска на попадание и расстояния
     */
    public record Mode(String code, String label, boolean rounds, boolean zone, boolean area, boolean planted) {
    }

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
