package app.charka.system;

import app.charka.model.Campaign;

import java.util.List;

/**
 * Игровая система (НРИ): правила, которые различаются между CP2020, D&D и т.п.
 * Ядро обращается к правилам только через этот интерфейс, реализации находит {@link GameSystemRegistry}.
 */
public interface GameSystem {

    /**
     * Код системы, хранится в {@code Campaign.system} («CP2020», «GENERIC»).
     */
    String getCode();

    /**
     * Название для интерфейса.
     */
    String getDisplayName();

    Currency getCurrency();

    /**
     * Типы счётчиков, создаваемых вместе с новым персонажем кампании этой системы.
     */
    default List<String> getDefaultCounterTypes() {
        return List.of();
    }

    /**
     * Выражения для быстрых кнопок броска ({@code DiceExpression}).
     */
    default List<String> getQuickDice() {
        return List.of("d4", "d6", "d8", "d10", "d12", "d20", "d100");
    }

    /**
     * Вызывается после промотки времени кампании (лечение, траты по правилам системы).
     */
    default void onTimePassed(Campaign campaign, int days) {
    }
}
