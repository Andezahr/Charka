package app.charka.item;

import java.util.List;

/**
 * Фишка под строкой предмета: одно нажатие вместо выбора из списка. Магазин, который вставляется в оружие; запас патронов.
 *
 * @param label    подпись: «40/40»
 * @param title    расшифровка
 * @param itemId   над каким предметом действие; null — над самим предметом строки или действия нет
 * @param action   код действия; null — фишка только показывает значение
 * @param sources  варианты параметра {@code source}: один подставляется сам, из нескольких фишка предлагает выбрать
 * @param catalog  {@code sources} — ключи справочника, нажатие добавляет предмет в этот инвентарь
 * @param disabled сейчас нажать нельзя, причина в {@code title}
 */
public record ItemChip(String label, String title, Long itemId, String action, List<ItemField.Option> sources,
                       boolean catalog, boolean disabled) {

    public ItemChip {
        sources = List.copyOf(sources);
    }

    public static ItemChip info(String label, String title) {
        return new ItemChip(label, title, null, null, List.of(), false, false);
    }

    public static ItemChip action(String label, String title, Long itemId, String action, List<ItemField.Option> sources) {
        return new ItemChip(label, title, itemId, action, sources, false, false);
    }

    public static ItemChip unavailable(String label, String reason) {
        return new ItemChip(label, reason, null, null, List.of(), false, true);
    }

    public static ItemChip fromCatalog(String label, String title, List<ItemField.Option> entries) {
        return new ItemChip(label, title, null, null, entries, true, false);
    }

    public boolean isPressable() {
        return !disabled && (action != null || catalog);
    }
}
