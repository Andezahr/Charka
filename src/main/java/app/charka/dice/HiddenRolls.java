package app.charka.dice;

import java.util.function.Supplier;

/**
 * Скрытый бросок мастера (A-09): всё, что попадёт в лог бросков внутри {@link #run}, игрокам не видно.
 * Броски пишутся глубоко (проверка навыка → проверка системы → лог), поэтому признак едет с потоком запроса,
 * а не параметром через все слои.
 */
public final class HiddenRolls {

    private static final ThreadLocal<Boolean> HIDDEN = ThreadLocal.withInitial(() -> false);

    private HiddenRolls() {
    }

    public static <T> T run(boolean hidden, Supplier<T> action) {
        if (!hidden) {
            return action.get();
        }
        boolean before = HIDDEN.get();
        HIDDEN.set(true);
        try {
            return action.get();
        } finally {
            HIDDEN.set(before);
        }
    }

    static boolean active() {
        return HIDDEN.get();
    }
}
