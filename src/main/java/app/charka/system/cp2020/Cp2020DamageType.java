package app.charka.system.cp2020;

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
    /** Бронебойная боеголовка ракеты (с. 111): SP вдвое меньше, прошедший урон не делится */
    ARMOR_PIERCING_WARHEAD("Бронебойная боеголовка"),
    /** В обход брони: падение, огонь, яд — SP не учитывается и не изнашивается */
    BYPASS("В обход брони"),
    /** Огнемёт (с. 110): жёсткая броня как обычно, мягкая защищает только от 16 SP и теряет 2 SP за попадание; см. Cp2020HealthStrategy */
    FLAME("Огнемёт");

    private final String label;

    public int effectiveSp(int sp) {
        return switch (this) {
            case NORMAL, FLAME -> sp;
            case ARMOR_PIERCING, ARMOR_PIERCING_WARHEAD -> sp / 2;
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
