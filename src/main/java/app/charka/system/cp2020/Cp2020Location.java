package app.charka.system.cp2020;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * Зоны попадания CP2020 и таблица бросков d10.
 */
@Getter
@RequiredArgsConstructor
public enum Cp2020Location {
    HEAD("Голова", "Голова", "1", 1, 1, false),
    TORSO("Торс", "Торс", "2–4", 2, 4, false),
    R_ARM("Правая рука", "Пр. рука", "5", 5, 5, true),
    L_ARM("Левая рука", "Лев. рука", "6", 6, 6, true),
    R_LEG("Правая нога", "Пр. нога", "7–8", 7, 8, true),
    L_LEG("Левая нога", "Лев. нога", "9–10", 9, 10, true);

    private final String label;
    /** Для узких карточек */
    private final String shortLabel;
    private final String rolls;
    private final int minRoll;
    private final int maxRoll;
    private final boolean limb;

    public static Cp2020Location fromRoll(int d10) {
        return Arrays.stream(values())
                .filter(l -> d10 >= l.minRoll && d10 <= l.maxRoll)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Бросок d10 вне диапазона 1–10: " + d10));
    }

    public static Cp2020Location parse(String code) {
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неизвестная зона попадания: " + code);
        }
    }
}
