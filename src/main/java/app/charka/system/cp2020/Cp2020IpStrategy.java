package app.charka.system.cp2020;

import app.charka.model.counters.strategy.AbstractCounterStrategy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Очки улучшения (Improvement Points) CP2020: значение — сколько очков доступно. Мастер начисляет их
 * на вкладке счётчиков, тратятся они на прокачку навыков ({@link Cp2020SkillProgression}).
 */
@Component
public class Cp2020IpStrategy extends AbstractCounterStrategy {

    public static final String TYPE = "CP2020_IP";

    @Override
    public String getCounterType() {
        return TYPE;
    }

    @Override
    public String getDisplayName() {
        return "Очки улучшения (Cyberpunk 2020)";
    }

    @Override
    public String getDefaultName() {
        return "Очки улучшения";
    }

    @Override
    public boolean isUniquePerCharacter() {
        return true;
    }

    @Override
    public int getDisplayOrder() {
        return 50;
    }

    @Override
    public Set<String> getSystems() {
        return Set.of(Cp2020System.CODE);
    }

    /**
     * Очков не может стать меньше нуля.
     */
    @Override
    public String getNewValue(String currentValue, Object inputValue) {
        return String.valueOf(Math.max(0, Integer.parseInt(currentValue) + (int) inputValue));
    }

    @Override
    public String getResetValue() {
        return "0";
    }
}
