package app.charka.item;

import app.charka.model.Item;

import java.util.List;

/**
 * Что вживлено в персонажа: не лежит в рюкзаке, поэтому показывается отдельным блоком фишек над инвентарями.
 *
 * @param title   заголовок
 * @param summary короткая строка: человечность и т.п.
 * @param rows    что установлено
 * @param addType тип предмета для окна «Добавить»
 * @param addAction действие над предметом сразу после выбора в окне
 * @param addConfirm что спросить перед этим: действие необратимо
 */
public record InstalledBlock(String title, String summary, List<Row> rows, String addType, String addAction, String addConfirm) {

    public InstalledBlock {
        rows = List.copyOf(rows);
    }

    /**
     * @param item    предмет
     * @param chip    подпись фишки: название и главное значение
     * @param details что показать в меню фишки
     * @param actions кнопки в меню фишки
     */
    public record Row(Item item, String chip, List<String> details, List<ItemAction> actions) {

        public Row {
            details = List.copyOf(details);
            actions = List.copyOf(actions);
        }
    }
}
