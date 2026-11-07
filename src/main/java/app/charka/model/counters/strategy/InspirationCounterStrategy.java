package app.charka.model.counters.strategy;

import app.charka.model.counters.Counter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * K-09 [CORE]: вдохновение — мастер выдаёт за отыгрыш (начисление всем со страницы кампании), игрок тратит одно,
 * чтобы перебросить любой бросок («↻ Перебросить» у итога атаки и проверки). Максимум держит мастер: предела нет.
 * Есть в Космозэках и в D&D 5e; доступно любой системе.
 */
@Component
public class InspirationCounterStrategy extends AbstractCounterStrategy {

    public static final String TYPE = "INSPIRATION";

    @Override
    public String getCounterType() {
        return TYPE;
    }

    @Override
    public String getDisplayName() {
        return "Вдохновение (переброс)";
    }

    @Override
    public String getDefaultName() {
        return "Вдохновение";
    }

    @Override
    public String getNewValue(String currentValue, Object inputValue) {
        return String.valueOf(Math.max(0, Integer.parseInt(currentValue) + (int) inputValue));
    }

    @Override
    public String getResetValue() {
        return "0";
    }

    @Override
    public boolean isAwardable() {
        return true;
    }

    @Override
    public boolean isUniquePerCharacter() {
        return true;
    }

    @Override
    public int getDisplayOrder() {
        return 30;
    }

    @Override
    public void initialize(Counter counter, Map<String, Integer> params) {
        counter.setValue(Math.max(0, counter.getValue() == null ? 0 : counter.getValue()));
    }
}
