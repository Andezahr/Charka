package app.charka.system.cp2020;

/**
 * Таблицы и формулы Cyberpunk 2020 (Friday Night Firefight) без состояния —
 * чтобы их можно было проверять отдельно от стратегии.
 * <p>
 * Уровни ранений нумеруются с 0: 0 — лёгкое, 1 — серьёзное, 2 — критическое,
 * 3..9 — смертельное 0..6; -1 — без ранений.
 */
public final class Cp2020Rules {

    public static final int BOXES_PER_LEVEL = 4;
    public static final int LEVEL_COUNT = 10;
    /** 10 уровней по 4 клетки; урон сверх этого — смерть */
    public static final int MAX_DAMAGE = BOXES_PER_LEVEL * LEVEL_COUNT;
    /** Индекс уровня «Смертельное 0» */
    public static final int MORTAL_START = 3;
    /** Урон за одно попадание сверх этого в голову убивает, в конечность — отрывает её */
    public static final int SEVERE_HIT_THRESHOLD = 8;
    /** Естественное восстановление при отдыхе, очков урона в день */
    public static final int NATURAL_HEALING_PER_DAY = 1;
    /** Человечность = EMP × 10; каждые потерянные 10 очков снижают EMP на 1 */
    public static final int HUMANITY_PER_EMP = 10;
    /** Домашнее правило (из Cyberpunk RED): новая роль открывается, когда спецспособность текущей достигла этого уровня */
    public static final int ROLE_UNLOCK_LEVEL = 4;

    private static final String[] LEVEL_NAMES = {
            "Лёгкое", "Серьёзное", "Критическое",
            "Смертельное 0", "Смертельное 1", "Смертельное 2", "Смертельное 3",
            "Смертельное 4", "Смертельное 5", "Смертельное 6"
    };

    private Cp2020Rules() {
    }

    public static int woundLevel(int damage) {
        if (damage <= 0) {
            return -1;
        }
        return Math.min((damage - 1) / BOXES_PER_LEVEL, LEVEL_COUNT - 1);
    }

    /**
     * @return номер смертельного уровня (0..6) или отрицательное число, если ранение не смертельное
     */
    public static int mortalLevel(int woundLevel) {
        return woundLevel - MORTAL_START;
    }

    public static boolean isMortal(int woundLevel) {
        return woundLevel >= MORTAL_START;
    }

    public static String levelName(int woundLevel) {
        return woundLevel < 0 ? "Без ранений" : LEVEL_NAMES[woundLevel];
    }

    /** CSS-класс степени тяжести для отрисовки */
    public static String levelCss(int woundLevel) {
        if (woundLevel < 0) return "ok";
        if (woundLevel == 0) return "light";
        if (woundLevel == 1) return "serious";
        if (woundLevel == 2) return "critical";
        return "mortal";
    }

    /**
     * Body Type Modifier — сколько урона «съедает» телосложение после брони.
     */
    public static int btm(int body) {
        if (body <= 2) return 0;
        if (body <= 4) return 1;
        if (body <= 7) return 2;
        if (body <= 9) return 3;
        if (body == 10) return 4;
        return 5;
    }

    /** Штраф к спасброску от оглушения: 0 за лёгкое, -1 за серьёзное и далее по уровню */
    public static int stunPenalty(int woundLevel) {
        return Math.max(woundLevel, 0);
    }

    /** REF: -2 при серьёзном ранении, /2 при критическом, /3 при смертельном (с округлением вверх) */
    public static int effectiveRef(int ref, int woundLevel) {
        if (woundLevel == 1) return Math.max(ref - 2, 0);
        return effectiveMentalOrRef(ref, woundLevel);
    }

    /** INT и COOL страдают начиная с критического ранения */
    public static int effectiveIntOrCool(int value, int woundLevel) {
        return effectiveMentalOrRef(value, woundLevel);
    }

    private static int effectiveMentalOrRef(int value, int woundLevel) {
        if (isMortal(woundLevel)) return ceilDiv(value, 3);
        if (woundLevel == 2) return ceilDiv(value, 2);
        return value;
    }

    /** Бег: MA × 3 метра за ход */
    public static int run(int ma) {
        return ma * 3;
    }

    /** Прыжок: бег / 4 метра */
    public static int leap(int run) {
        return run / 4;
    }

    /** Переносимый вес: BODY × 10 кг */
    public static int carry(int body) {
        return body * 10;
    }

    /** Поднять / рывок: BODY × 40 кг */
    public static int lift(int body) {
        return body * 40;
    }

    /** Текущая человечность, не ниже 0 */
    public static int humanity(int emp, int humanityLoss) {
        return Math.max(0, emp * HUMANITY_PER_EMP - humanityLoss);
    }

    /** Текущий EMP по человечности: человечность / 10 с округлением вверх (0 — киберпсихоз) */
    public static int effectiveEmp(int humanity) {
        return ceilDiv(humanity, HUMANITY_PER_EMP);
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
