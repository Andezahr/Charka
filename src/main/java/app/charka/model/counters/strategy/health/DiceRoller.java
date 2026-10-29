package app.charka.model.counters.strategy.health;

/**
 * Источник бросков кубиков. Вынесен в интерфейс, чтобы в тестах подставлять заранее известные броски.
 */
public interface DiceRoller {

    /**
     * @return случайное число от 1 до sides включительно
     */
    int roll(int sides);

    default int d10() {
        return roll(10);
    }
}
