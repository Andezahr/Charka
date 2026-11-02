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
 * @param sourceLabel что выбирают в {@code sources}; null — патроны
 * @param amountLabel что значит число; null — «Сколько»
 */
public record ItemAction(String code, String label, String unavailable, List<ItemField.Option> sources,
                         Integer amount, Integer maxAmount, boolean secondary, boolean hidden, boolean combat, String confirm,
                         String sourceLabel, String amountLabel) {

    public ItemAction {
        sources = List.copyOf(sources);
    }

    public ItemAction(String code, String label, String unavailable, List<ItemField.Option> sources,
                      Integer amount, Integer maxAmount, boolean secondary, boolean hidden, boolean combat, String confirm) {
        this(code, label, unavailable, sources, amount, maxAmount, secondary, hidden, combat, confirm, null, null);
    }

    /** Подписи выбора и числа, когда это не патроны и не количество */
    public ItemAction withLabels(String source, String number) {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, secondary, hidden, combat, confirm, source, number);
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

    /** Выбор и число сразу */
    public static ItemAction withSourcesAndAmount(String code, String label, List<ItemField.Option> sources, int amount, int maxAmount) {
        return new ItemAction(code, label, null, sources, amount, maxAmount, false, false, false, null);
    }

    public ItemAction asSecondary() {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, true, hidden, combat, confirm, sourceLabel, amountLabel);
    }

    /** Просто кнопка: ни выбора, ни числа, поэтому помещается в одну линию с названием предмета */
    public boolean isPlain() {
        return sources.isEmpty() && amount == null;
    }

    public ItemAction asHidden() {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, secondary, true, combat, confirm, sourceLabel, amountLabel);
    }

    public ItemAction forCombat() {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, secondary, hidden, true, confirm, sourceLabel, amountLabel);
    }

    public ItemAction withConfirm(String text) {
        return new ItemAction(code, label, unavailable, sources, amount, maxAmount, secondary, hidden, combat, text, sourceLabel, amountLabel);
    }

    /** Кнопка в строке участника боя: то, что на листе делает фишка, здесь показывается, только если его можно сделать */
    public boolean isShownInCombat() {
        return !secondary && (!hidden || (combat && unavailable == null));
    }
}
