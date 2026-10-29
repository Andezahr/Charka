package app.charka.model.counters.strategy.health.cp2020;

import lombok.Value;

import java.util.List;

/**
 * Данные карточки брони CP2020.
 */
@Value
public class Cp2020ArmorView {

    List<ArmorLine> locations;
    /** Хоть одна зона изношена — показываем кнопку ремонта */
    boolean worn;

    @Value
    public static class ArmorLine {
        String code;
        String label;
        String shortLabel;
        String rolls;
        int sp;
        int maxSp;

        public boolean isWorn() {
            return sp < maxSp;
        }
    }
}
