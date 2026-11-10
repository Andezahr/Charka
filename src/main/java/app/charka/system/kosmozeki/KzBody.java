package app.charka.system.kosmozeki;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Набор зон тела существа («Человек», «Зверь», свой набор мастера). Хранится текстом в счётчике {@link KzBodyStrategy}:
 * строка на зону {@code код|название|цифры кубика|ХП|множитель урона}.
 *
 * @param zones зоны в порядке показа
 */
public record KzBody(List<Zone> zones) {

    /**
     * Зона тела.
     *
     * @param code       код для attributes счётчика: латиница и цифры
     * @param name       название
     * @param digits     последние цифры к100, при которых случайное попадание идёт сюда; пусто — только прицельно или по решению мастера
     * @param maxHp      полное ХП
     * @param multiplier во сколько раз умножается урон после брони (голова ×2)
     */
    public record Zone(String code, String name, Set<Integer> digits, int maxHp, int multiplier) {

        public Zone {
            digits = Set.copyOf(digits);
        }

        /** «0», «5–9», «1, 3» — для подписей */
        public String digitsLabel() {
            List<Integer> sorted = digits.stream().sorted().toList();
            if (sorted.isEmpty()) {
                return "—";
            }
            if (sorted.size() > 2 && sorted.get(sorted.size() - 1) - sorted.get(0) == sorted.size() - 1) {
                return sorted.get(0) + "–" + sorted.get(sorted.size() - 1);
            }
            return sorted.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
    }

    public static final String HEAD = "head";
    public static final String BODY = "body";
    public static final String RIGHT_ARM = "rarm";
    public static final String LEFT_ARM = "larm";
    public static final String RIGHT_LEG = "rleg";
    public static final String LEFT_LEG = "lleg";

    /** Человек — лист персонажа из документа системы: конечности по 100, тело 150, голова ×2 */
    public static final KzBody HUMAN = new KzBody(List.of(
            new Zone(HEAD, "Голова", Set.of(0), 100, 2),
            new Zone(RIGHT_ARM, "Правая рука", Set.of(1), 100, 1),
            new Zone(LEFT_ARM, "Левая рука", Set.of(2), 100, 1),
            new Zone(RIGHT_LEG, "Правая нога", Set.of(3), 100, 1),
            new Zone(LEFT_LEG, "Левая нога", Set.of(4), 100, 1),
            new Zone(BODY, "Тело", Set.of(5, 6, 7, 8, 9), 150, 1)));

    /** HOMEBREW: четвероногий зверь — заготовка для мастера, числа правятся у каждого зверя */
    public static final KzBody BEAST = new KzBody(List.of(
            new Zone(HEAD, "Голова", Set.of(0), 80, 2),
            new Zone("fleg", "Передние лапы", Set.of(1, 2), 80, 1),
            new Zone("hleg", "Задние лапы", Set.of(3, 4), 80, 1),
            new Zone(BODY, "Тело", Set.of(5, 6, 7, 8, 9), 150, 1)));

    /** HOMEBREW: боевой робот — заготовка: прочный корпус, хрупкие сенсоры */
    public static final KzBody ROBOT = new KzBody(List.of(
            new Zone(HEAD, "Сенсоры", Set.of(0), 50, 2),
            new Zone("arms", "Манипуляторы", Set.of(1, 2), 100, 1),
            new Zone("legs", "Ходовая", Set.of(3, 4), 120, 1),
            new Zone(BODY, "Корпус", Set.of(5, 6, 7, 8, 9), 200, 1)));

    /**
     * Готовые наборы: код → подпись и набор.
     */
    public record Preset(String code, String label, KzBody body) {
    }

    public static final List<Preset> PRESETS = List.of(
            new Preset("HUMAN", "Человек", HUMAN),
            new Preset("BEAST", "Зверь", BEAST),
            new Preset("ROBOT", "Робот", ROBOT));

    public static Optional<Preset> preset(String code) {
        return PRESETS.stream().filter(preset -> preset.code().equals(code)).findFirst();
    }

    public KzBody {
        zones = List.copyOf(zones);
    }

    public Optional<Zone> zone(String code) {
        return zones.stream().filter(zone -> zone.code().equals(code)).findFirst();
    }

    /** Куда идёт случайное попадание с этой последней цифрой; пусто — у существа такой зоны нет */
    public Optional<Zone> byDigit(int digit) {
        return zones.stream().filter(zone -> zone.digits().contains(digit)).findFirst();
    }

    /** Текст для счётчика */
    public String serialize() {
        return zones.stream()
                .map(zone -> String.join("|", zone.code(), zone.name().replace("|", "/").replace("\n", " "),
                        zone.digits().stream().sorted().map(String::valueOf).collect(Collectors.joining(",")),
                        String.valueOf(zone.maxHp()), String.valueOf(zone.multiplier())))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Набор из текста счётчика; битая строка пропускается. Пусто или null — человек.
     */
    public static KzBody parse(String text) {
        if (text == null || text.isBlank()) {
            return HUMAN;
        }
        List<Zone> zones = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 5) {
                continue;
            }
            try {
                zones.add(new Zone(parts[0].strip(), parts[1].strip(), digits(parts[2]),
                        Integer.parseInt(parts[3].strip()), Integer.parseInt(parts[4].strip())));
            } catch (NumberFormatException e) {
                // битая строка — пропускаем, остальные зоны работают
            }
        }
        return zones.isEmpty() ? HUMAN : new KzBody(zones);
    }

    /**
     * Цифры кубика из ввода мастера: «0», «5-9», «1, 3», «5–9».
     *
     * @throws IllegalArgumentException если это не цифры 0–9
     */
    public static Set<Integer> digits(String text) {
        Set<Integer> digits = new LinkedHashSet<>();
        if (text == null) {
            return digits;
        }
        for (String part : text.replace('–', '-').replace('—', '-').split("[,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            String[] range = part.split("-");
            try {
                int from = Integer.parseInt(range[0].strip());
                int to = range.length > 1 ? Integer.parseInt(range[1].strip()) : from;
                if (from < 0 || to > 9 || from > to) {
                    throw new IllegalArgumentException("Цифры кубика — от 0 до 9: " + part);
                }
                for (int digit = from; digit <= to; digit++) {
                    digits.add(digit);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Цифры кубика — от 0 до 9: " + part);
            }
        }
        return digits;
    }
}
