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
 * @param secondary   нужно редко: кнопка свёрнута
 * @param hidden      кнопки нет, действие вызывается фишкой ({@link ItemChip})
 */
public record ItemAction(String code, String label, String unavailable, List<ItemField.Option> sources,
                         Integer amount, Integer maxAmount, boolean secondary, boolean hidden) {

    public ItemAction {
        sources = List.copyOf(sources);
    }

    public static ItemAction simple(String code, String label, String unavailable) {
        return new ItemAction(code, label, unavailable, List.of(), null, null, false, false);
    }

    public static ItemAction withSources(String code, String label, String unavailable, List<ItemField.Option> sources) {
        return new ItemAction(code, label, unavailable, sources, null, null, false, false);
    }

    public static ItemAction withAmount(String code, String label, String unavailable, int amount, int maxAmount) {
        return new ItemAction(code, label, unavailable, List.of(), amount, maxAmount, false, false);
    }

    public ItemAction asSecondary() {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, true, hidden);
    }

    /** Просто кнопка: ни выбора, ни числа, поэтому помещается в одну линию с названием предмета */
    public boolean isPlain() {
        return sources.isEmpty() && amount == null;
    }

    public ItemAction asHidden() {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, secondary, true);
    }
}
