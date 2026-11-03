package app.charka.system.cp2020;

import java.util.List;
import java.util.Map;

/**
 * Ключевые приёмы боевых искусств: таблица с. 100, значения прочитаны с изображения страницы.
 */
final class Cp2020MartialArts {

    enum Move { STRIKE, KICK, BLOCK, DODGE, THROW, HOLD, ESCAPE, CHOKE, SWEEP, GRAPPLE }

    private static final Map<String, int[]> KEY_ATTACKS = Map.ofEntries(
            // удар, пинок, блок, уклонение, бросок, удержание, освобождение, удушение, подсечка, захват
            Map.entry("karate", new int[]{2, 2, 2, 0, 0, 0, 0, 0, 0, 0}),
            Map.entry("judo", new int[]{0, 0, 0, 1, 3, 2, 2, 0, 2, 2}),
            Map.entry("boxing", new int[]{3, 0, 3, 1, 0, 0, 0, 0, 0, 0}),
            Map.entry("thai_kick_boxing", new int[]{3, 3, 2, 0, 0, 0, 0, 0, 0, 1}),
            Map.entry("choi_li_fut", new int[]{2, 2, 2, 1, 1, 0, 0, 0, 2, 0}),
            Map.entry("aikido", new int[]{0, 0, 4, 3, 3, 3, 3, 1, 3, 2}),
            Map.entry("animal_kung_fu", new int[]{2, 2, 2, 0, 0, 0, 0, 0, 1, 0}),
            Map.entry("tae_kwon_do", new int[]{3, 3, 2, 1, 0, 0, 0, 0, 2, 0}),
            Map.entry("savate", new int[]{0, 4, 1, 1, 0, 0, 0, 0, 0, 0}),
            Map.entry("wrestling", new int[]{0, 0, 0, 0, 3, 4, 4, 2, 2, 4}),
            Map.entry("capoeira", new int[]{1, 2, 2, 2, 0, 0, 0, 0, 3, 0}));

    /** Для выбора в форме, в порядке таблицы */
    static final List<String> STYLES = List.of("karate", "judo", "boxing", "thai_kick_boxing", "choi_li_fut", "aikido",
            "animal_kung_fu", "tae_kwon_do", "savate", "wrestling", "capoeira");

    private Cp2020MartialArts() {
    }

    static boolean isStyle(String skillCode) {
        return KEY_ATTACKS.containsKey(skillCode);
    }

    /** @return прибавка стиля к приёму; 0 — не стиль или приём для него не ключевой */
    static int keyBonus(String skillCode, Move move) {
        int[] bonuses = KEY_ATTACKS.get(skillCode);
        return bonuses == null ? 0 : bonuses[move.ordinal()];
    }
}
