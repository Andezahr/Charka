package app.charka.check;

import app.charka.model.Character;

/**
 * Удача: сколько осталось, потратить на бросок, вернуть к новой сессии
 * <p>
 * ARC-01: часть правил системы ({@code GameSystem#luck()}); каждый метод по умолчанию — «у системы этого нет».
 */
public interface LuckRules {

    /** Система без этой части */
    LuckRules NONE = new LuckRules() {
    };

    /**
     * Очки, которые игрок прибавляет к важному броску, объявив до него (CP2020: Удача, с. 26). 0 — нет.
     */
    default int getLuck(Character character) {
        return 0;
    }

    /**
     * Потратить очки на бросок.
     *
     * @throws IllegalArgumentException если столько нет
     */
    default void spendLuck(Character character, int points) {
        if (points > 0) {
            throw new IllegalArgumentException("Удачи в этой системе нет");
        }
    }

    /** Есть ли в системе удача: на странице кампании появляется «Новая сессия» */
    default boolean hasLuck() {
        return false;
    }

    /** Новая игровая сессия: потраченные очки возвращаются */
    default void restoreLuck(Character character) {
    }
}
