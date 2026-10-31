package app.charka.attack;

import app.charka.item.ItemField.Option;

import java.util.List;

/**
 * Что спросить у игрока для этой атаки. Всё, что зависит от системы, включая подписи, задаёт система.
 *
 * @param damage        урон для заголовка диалога
 * @param range         дальность, м; null — не указана
 * @param unavailable   почему сейчас нельзя атаковать; null — можно
 * @param modes         режимы; если вариант один, выбирать нечего
 * @param maxRounds     сколько можно выпустить или израсходовать за раз
 * @param defaultRounds сколько предложить сразу
 * @param roundsLabel   что считает это число: патроны, пятна, единицы взрывчатки
 * @param skills        навык атаки; если вариант один, выбирать нечего
 * @param locationLabel подпись выбора зоны; null — зону не выбирают
 * @param locations     зоны для выбора
 * @param distanceLabel подпись расстояния; null — расстояние не спрашивается
 * @param bands         расстояния одним нажатием, чтобы не набирать число
 * @param sections      модификаторы; редкие показываются по запросу
 * @param actionLabel   подпись кнопки: что случится с предметом
 */
public record AttackForm(String damage, Integer range, String unavailable, List<Mode> modes, int maxRounds, int defaultRounds,
                         String roundsLabel, List<Option> skills, String locationLabel, List<Option> locations,
                         String distanceLabel, List<Band> bands, List<Section> sections, String actionLabel) {

    /**
     * @param code           код режима
     * @param label          подпись
     * @param rounds         режим спрашивает число патронов или единиц
     * @param width          спрашивается ширина зоны
     * @param multiTarget    целей может быть несколько
     * @param noRoll         нет броска на попадание и расстояния: заряд на месте
     * @param chooseLocation можно выбрать зону попадания
     * @param noDistance     бросок есть, а расстояния нет: удар рукой
     */
    public record Mode(String code, String label, boolean rounds, boolean width, boolean multiTarget, boolean noRoll, boolean chooseLocation, boolean noDistance) {

        public Mode(String code, String label, boolean rounds, boolean width, boolean multiTarget, boolean noRoll, boolean chooseLocation) {
            this(code, label, rounds, width, multiTarget, noRoll, chooseLocation, false);
        }
    }

    /**
     * @param meters расстояние
     * @param label  подпись кнопки
     */
    public record Band(int meters, String label) {
    }

    /**
     * @param name    название раздела
     * @param choices выборы раздела
     * @param rare    нужно редко: свёрнут
     */
    public record Section(String name, List<Choice> choices, boolean rare) {
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
