package app.charka.model.counters.strategy.health.cp2020;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы урона CP2020 — отличаются тем, как работают с бронёй.
 */
@Getter
@RequiredArgsConstructor
public enum Cp2020DamageType {
    /** Обычный: броня вычитается полностью */
    NORMAL("Обычный"),
    /** Бронебойный: броня считается за половину SP, прошедший урон тоже делится пополам */
    ARMOR_PIERCING("Бронебойный (AP)"),
    /** В обход брони: падение, огонь, яд — SP не учитывается и не изнашивается */
    BYPASS("В обход брони");

    private final String label;

    public int effectiveSp(int sp) {
        return switch (this) {
            case NORMAL -> sp;
            case ARMOR_PIERCING -> sp / 2;
            case BYPASS -> 0;
        };
    }

    public static Cp2020DamageType parse(String code) {
        if (code == null || code.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неизвестный тип урона: " + code);
        }
    }
}
