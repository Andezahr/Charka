package app.charka.item;

import app.charka.model.Item;
import app.charka.system.Currency;

/**
 * Итог покупки: предмет уже в инвентаре, цена списана записью в деньгах персонажа.
 *
 * @param item     добавленный предмет (у штучного — последняя строка)
 * @param paid     сколько списано; 0 — у предмета нет цены, запись не создана
 * @param balance  сколько осталось на счету
 * @param currency валюта системы кампании
 */
public record Purchase(Item item, long paid, long balance, Currency currency) {

    /** Строка для показа под предметом */
    public String summary() {
        if (paid == 0) {
            return "Цены нет — деньги не списаны";
        }
        return "Куплено за " + paid + " " + currency.symbol() + ", осталось " + balance + " " + currency.symbol();
    }
}
