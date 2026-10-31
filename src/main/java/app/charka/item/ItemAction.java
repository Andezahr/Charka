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
 * @param combat      нужно и в бою: фишек в трекере нет, там это кнопка
 * @param confirm     что спросить перед действием: оно необратимо; null — не спрашивать
 */
public record ItemAction(String code, String label, String unavailable, List<ItemField.Option> sources,
                         Integer amount, Integer maxAmount, boolean secondary, boolean hidden, boolean combat, String confirm) {

    public ItemAction {
        sources = List.copyOf(sources);
    }

    public static ItemAction simple(String code, String label, String unavailable) {
        return new ItemAction(code, label, unavailable, List.of(), null, null, false, false, false, null);
    }

    public static ItemAction withSources(String code, String label, String unavailable, List<ItemField.Option> sources) {
        return new ItemAction(code, label, unavailable, sources, null, null, false, false, false, null);
    }

    public static ItemAction withAmount(String code, String label, String unavailable, int amount, int maxAmount) {
        return new ItemAction(code, label, unavailable, List.of(), amount, maxAmount, false, false, false, null);
    }

    public ItemAction asSecondary() {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, true, hidden, combat, confirm);
    }

    /** Просто кнопка: ни выбора, ни числа, поэтому помещается в одну линию с названием предмета */
    public boolean isPlain() {
        return sources.isEmpty() && amount == null;
    }

    public ItemAction asHidden() {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, secondary, true, combat, confirm);
    }

    public ItemAction forCombat() {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, secondary, hidden, true, confirm);
    }

    public ItemAction withConfirm(String text) {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, secondary, hidden, combat, text);
    }

    /** Кнопка в строке участника боя: то, что на листе делает фишка, здесь показывается, только если его можно сделать */
    public boolean isShownInCombat() {
        return !secondary && (!hidden || (combat && unavailable == null));
    }
}
