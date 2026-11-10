package app.charka.model.counters.strategy.health;

import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.CounterStrategy;

/**
 * Счётчик здоровья — точка расширения для разных игровых систем (CP2020, D&D…).
 * <p>
 * Это обычная {@link CounterStrategy}, поэтому реализация регистрируется в
 * {@link app.charka.model.counters.strategy.CounterStrategyFactory} автоматически,
 * а базовые операции счётчика (±N, сброс) продолжают работать.
 * Значение счётчика ({@link Counter#getValue()}) — полученный урон. Характеристики, броня и т.п.
 * живут в отдельных счётчиках: система объявляет их в {@link #getRequiredCounterTypes()}
 * и получает через {@link CounterContext}.
 * <p>
 * Методы, не имеющие смысла для системы, бросают {@link UnsupportedOperationException}.
 */
public interface HealthCounterStrategy extends CounterStrategy {

    /** Цель уже мертва: новый урон к ней не применяется */
    boolean isDead(CounterContext context);

    /** Нанести урон по правилам системы (броня, зоны, модификаторы) */
    HealthEvent applyDamage(CounterContext context, DamageRequest request);

    /** Вылечить указанное количество урона */
    HealthEvent heal(CounterContext context, int points);

    /**
     * GM-01: лечение одной зоны (правка мастера с карты боя). Система без зон лечит целиком.
     *
     * @param location код зоны; пусто — как {@link #heal(CounterContext, int)}
     */
    default HealthEvent heal(CounterContext context, int points, String location) {
        return heal(context, points);
    }

    /** GM-01: лечится ли зона отдельно — тогда в форме лечения мастера есть выбор зоны */
    default boolean healsByLocation() {
        return false;
    }

    /** Естественное восстановление за прошедшие игровые дни */
    HealthEvent healOverTime(CounterContext context, int days);

    /**
     * Спасбросок/проверка.
     *
     * @param saveType код проверки, свой у каждой системы (в CP2020 — STUN, DEATH)
     * @param roll     результат броска игрока; null — бросает сервер
     */
    HealthEvent save(CounterContext context, String saveType, Integer roll);

    /**
     * Спасбросок с прибавкой к числу: например, от оглушающей гранаты.
     */
    default HealthEvent save(CounterContext context, String saveType, Integer roll, int modifier) {
        return save(context, saveType, roll);
    }

    /** То же с удачей, объявленной до броска; у систем без удачи она не учитывается */
    default HealthEvent save(CounterContext context, String saveType, Integer roll, int modifier, int luck) {
        return save(context, saveType, roll, modifier);
    }

    /** Стабилизировать умирающего (результат медицинской помощи) */
    HealthEvent stabilize(CounterContext context);

    /** Какую медпомощь бывает: проверка навыка медика против состояния пациента. Пусто — только ручные кнопки */
    default java.util.List<CareOption> getCareOptions() {
        return java.util.List.of();
    }

    /** Сложность проверки медика; null — помощь сейчас не нужна */
    default Integer careDifficulty(CounterContext context, CareOption option) {
        return null;
    }

    /** Итог проверки медика: что меняется у пациента */
    default HealthEvent applyCare(CounterContext context, CareOption option, boolean success) {
        throw new UnsupportedOperationException("Медпомощи в этой системе нет");
    }

    /** K-08: как персонаж отдыхает при промотке времени (Космозэки: сон, сон и еда); пусто — выбора нет */
    default java.util.List<app.charka.item.ItemField.Option> getRestOptions() {
        return java.util.List.of();
    }

    /** Текущий выбор отдыха — значение из {@link #getRestOptions()} */
    default String getRest(Counter counter) {
        return null;
    }

    /** Запомнить выбор отдыха; незнакомое значение — ошибка */
    default void setRest(Counter counter, String rest) {
        throw new UnsupportedOperationException("Выбора отдыха нет");
    }

    @Override
    HealthView describe(CounterContext context);
}
