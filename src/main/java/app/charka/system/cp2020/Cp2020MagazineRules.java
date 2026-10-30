package app.charka.system.cp2020;

import java.util.List;

/**
 * Правила магазинов. Основа — Solo of Fortune II, статья «Fully Chipped», раздел «Magazines» (читалось по сканеру, сверить с книгой).
 * В основной книге цен на магазины нет. Всё, что придумано нами, помечено HOMEBREW; список — в TODO.md, «Домашние правила».
 */
final class Cp2020MagazineRules {

    /** Solo of Fortune II: магазин не больше пяти стандартных */
    static final int MAX_EXTENSION = 5;

    /** Solo of Fortune II: штрафы, когда магазин больше двух стандартных */
    static final int PENALTY_ABOVE = 2;

    /** HOMEBREW: какие расширенные размеры есть в справочнике; в форме предмета можно задать любой */
    static final List<Integer> CATALOG_EXTENSIONS = List.of(2, MAX_EXTENSION);

    /**
     * Цена за патрон вместимости. Solo of Fortune II: безгильзовые 0.5 eb, с гильзой 1 eb, тяжёлое оружие 2–3 eb на усмотрение мастера.
     * HOMEBREW: у тяжёлого взята нижняя граница; цена считается за всю вместимость, а не только за прибавку.
     */
    enum Rate {
        CASELESS(0.5), CASED(1), HEAVY(2);

        private final double perRound;

        Rate(double perRound) {
            this.perRound = perRound;
        }
    }

    private static final List<String> RELIABILITY = List.of("VR", "ST", "UR");
    private static final List<String> CONCEALABILITY = List.of("P", "J", "L", "N");

    private Cp2020MagazineRules() {
    }

    /**
     * HOMEBREW: стандартный запасной магазин стоит по той же ставке, что и расширенный. Дробная цена округляется вверх: у предмета цена целая.
     */
    static int price(int capacity, Rate rate) {
        return (int) Math.ceil(capacity * rate.perRound);
    }

    /**
     * Встаёт ли магазин в оружие. Верхняя граница — Solo of Fortune II.
     * HOMEBREW: магазин меньше стандартного не подходит.
     */
    static boolean fits(int standard, int capacity) {
        return capacity >= standard && capacity <= standard * MAX_EXTENSION;
    }

    static boolean isOversized(int standard, int capacity) {
        return capacity > standard * PENALTY_ABOVE;
    }

    /** Надёжность на уровень ниже, не хуже самой низкой */
    static String lowerReliability(String code) {
        return lower(RELIABILITY, code);
    }

    /** Скрытность на уровень хуже, не хуже самой низкой */
    static String lowerConcealability(String code) {
        return lower(CONCEALABILITY, code);
    }

    /**
     * Snapshot-инициатива с большим магазином. Solo of Fortune II: пистолеты и ПП +1, винтовки и дробовики −2.
     * Плюс выглядит странно для громоздкого магазина: возможна ошибка распознавания, сверить с книгой.
     */
    static int snapshotModifier(String category) {
        return switch (category == null ? "" : category) {
            case "P", "SMG" -> 1;
            case "RIF", "SHT" -> -2;
            default -> 0;
        };
    }

    private static String lower(List<String> levels, String code) {
        int level = levels.indexOf(code);
        return level < 0 ? code : levels.get(Math.min(level + 1, levels.size() - 1));
    }
}
