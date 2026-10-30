package app.charka.item;

import java.util.List;

/**
 * Действие над предметом на листе персонажа: выстрел, перезарядка. Список даёт {@code GameSystem.getItemActions(Item)}.
 *
 * @param code        код действия, приходит в {@code GameSystem.performItemAction}
 * @param label       подпись кнопки
 * @param unavailable почему действие сейчас нельзя выполнить; null — можно
 * @param sources     из чего выбирать (параметр {@code source}); пусто — выбора нет
 * @param amount      начальное значение числа (параметр {@code amount}); null — числа нет
 * @param maxAmount   наибольшее значение числа
 */
public record ItemAction(String code, String label, String unavailable, List<ItemField.Option> sources,
                         Integer amount, Integer maxAmount) {

    public ItemAction {
        sources = List.copyOf(sources);
    }

    public static ItemAction simple(String code, String label, String unavailable) {
        return new ItemAction(code, label, unavailable, List.of(), null, null);
    }

    public static ItemAction withSources(String code, String label, String unavailable, List<ItemField.Option> sources) {
        return new ItemAction(code, label, unavailable, sources, null, null);
    }

    public static ItemAction withAmount(String code, String label, String unavailable, int amount, int maxAmount) {
        return new ItemAction(code, label, unavailable, List.of(), amount, maxAmount);
    }
}
