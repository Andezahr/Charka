package app.charka.system.cp2020;

import app.charka.model.counters.strategy.AbstractCounterStrategy;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Репутация CP2020 (с. 55): 0–10, чем больше, тем больше людей о персонаже слышали. Дурная слава тоже репутация.
 * Трус — с минусом: в Facedown его репутация вычитается.
 */
@Component
public class Cp2020RepStrategy extends AbstractCounterStrategy {

    public static final String TYPE = "CP2020_REP";
    static final int MAX = 10;

    @Override
    public String getCounterType() {
        return TYPE;
    }

    @Override
    public String getDisplayName() {
        return "Репутация (Cyberpunk 2020)";
    }

    @Override
    public String getDefaultName() {
        return "Репутация";
    }

    @Override
    public boolean isUniquePerCharacter() {
        return true;
    }

    @Override
    public int getDisplayOrder() {
        return 40;
    }

    @Override
    public Set<String> getSystems() {
        return Set.of(Cp2020System.CODE);
    }

    /**
     * От −10 (известный трус) до 10.
     */
    @Override
    public String getNewValue(String currentValue, Object inputValue) {
        return String.valueOf(Math.clamp(Integer.parseInt(currentValue) + (long) (int) inputValue, -MAX, MAX));
    }

    @Override
    public String getResetValue() {
        return "0";
    }
}
