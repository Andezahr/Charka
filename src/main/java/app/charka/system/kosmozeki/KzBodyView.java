package app.charka.system.kosmozeki;

import app.charka.item.ItemField.Option;
import app.charka.model.counters.strategy.health.HealthView;

import java.util.List;

/**
 * Карточка тела Космозэков: зоны с ХП и бронёй, состояние.
 *
 * @param damage      потеряно ХП всего
 * @param maxDamage   ХП всех зон вместе
 * @param statusLabel «Цел», «Ранен», «Без сознания», «Мёртв»
 * @param statusCss   класс цвета: ok, hurt, out, dead
 * @param unconscious зона на нуле, и мастер не поднял
 * @param awake       мастер отметил «в сознании», хотя зона на нуле
 * @param rest        как отдыхает при промотке времени: 0 — без отдыха, 1 — сон, 2 — сон и еда
 * @param zones       зоны по порядку набора
 * @param zonesText   набор зон для правки: строка на зону
 * @param presets     готовые наборы
 */
public record KzBodyView(int damage, int maxDamage, String statusLabel, String statusCss, boolean dead, boolean unconscious,
                         boolean awake, int rest, List<ZoneRow> zones, String zonesText, List<Option> presets,
                         List<Option> restOptions) implements HealthView {

    /**
     * @param armorFrom откуда броня: название надетого предмета или «вручную»; null — брони нет
     */
    public record ZoneRow(String code, String name, String digits, int hp, int maxHp, int armor, String armorFrom, int multiplier,
                          int percent) {

        public boolean isDown() {
            return hp <= 0;
        }

        /** Цвет полоски: целая, ранена, тяжело, на нуле */
        public String getCss() {
            return hp <= 0 ? "down" : percent < 35 ? "low" : percent < 100 ? "hurt" : "ok";
        }
    }

    @Override
    public int getDamage() {
        return damage;
    }

    @Override
    public int getMaxDamage() {
        return maxDamage;
    }

    @Override
    public String getStatusLabel() {
        return statusLabel;
    }

    @Override
    public boolean isDead() {
        return dead;
    }
}
