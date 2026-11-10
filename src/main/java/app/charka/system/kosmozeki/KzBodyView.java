package app.charka.system.kosmozeki;

import app.charka.item.ItemField.Option;
import app.charka.model.counters.strategy.health.HealthView;

import java.util.List;

/**
 * Карточка тела Космозэков: зоны с ХП и бронёй, состояние.
 *
 * @param damage      потеряно ХП всего
 * @param maxDamage   ХП всех зон вместе
 * @param statusLabel «Без ранений», «Ранен», «Без сознания», «Мёртв»
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
     * @param digitSet  цифры к10, которые попадают в зону (UX-26: полоса 0…9)
     */
    public record ZoneRow(String code, String name, String digits, int hp, int maxHp, int armor, String armorFrom, int multiplier,
                          int percent, List<Integer> digitSet) {

        public boolean isDown() {
            return hp <= 0;
        }

        /** Цвет полоски: целая, ранена, тяжело, на нуле */
        public String getCss() {
            return hp <= 0 ? "down" : percent < 35 ? "low" : percent < 100 ? "hurt" : "ok";
        }
    }

    /**
     * UX-26: клетка полосы «последняя цифра броска к100 — зона».
     *
     * @param zone зона этой цифры; null — цифра не попадает ни в одну зону (свой набор зон)
     */
    public record DigitCell(int digit, ZoneRow zone) {
        public String getCss() {
            return zone == null ? "none" : zone.getCss();
        }

        public String getLabel() {
            return zone == null ? "мимо зон" : zone.name();
        }
    }

    /** Цифры 0…9 и их зоны — по ним мастер читает зону попадания с кубика */
    public List<DigitCell> getDigitCells() {
        return java.util.stream.IntStream.range(0, 10)
                .mapToObj(digit -> new DigitCell(digit, zones.stream().filter(zone -> zone.digitSet().contains(digit)).findFirst().orElse(null)))
                .toList();
    }

    /**
     * Набор зон человека (голова, руки, ноги, тело): рисуется фигурой. Иначе (звери, роботы, свой набор) — зоны рядом по порядку цифр.
     */
    public boolean isHumanShape() {
        java.util.Set<String> codes = zones.stream().map(ZoneRow::code).collect(java.util.stream.Collectors.toSet());
        return codes.equals(java.util.Set.of(KzBody.HEAD, KzBody.BODY, KzBody.RIGHT_ARM, KzBody.LEFT_ARM, KzBody.RIGHT_LEG, KzBody.LEFT_LEG));
    }

    /** Зоны на нуле — для строки «Без сознания: …» */
    public String getDownZones() {
        return zones.stream().filter(ZoneRow::isDown).map(zone -> zone.name().toLowerCase()).collect(java.util.stream.Collectors.joining(", "));
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
