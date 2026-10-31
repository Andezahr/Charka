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

    /** Стабилизировать умирающего (результат медицинской помощи) */
    HealthEvent stabilize(CounterContext context);

    @Override
    HealthView describe(CounterContext context);
}
