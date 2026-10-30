package app.charka.attack;

import app.charka.model.Item;

/**
 * Чем атакуют: предметом или тем, что предметом не является (заклинание, встроенное оружие).
 *
 * @param name    название для расчёта и лога
 * @param item    предмет; null — не предмет
 * @param ability код способности в системе; null — это предмет
 */
public record AttackSource(String name, Item item, String ability) {

    public static AttackSource of(Item item) {
        return new AttackSource(item.getName(), item, null);
    }

    public static AttackSource ability(String code, String name) {
        return new AttackSource(name, null, code);
    }
}
