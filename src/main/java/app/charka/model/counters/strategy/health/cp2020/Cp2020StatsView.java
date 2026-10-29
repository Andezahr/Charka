package app.charka.model.counters.strategy.health.cp2020;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Данные карточки характеристик CP2020: статы с текущими значениями и производные величины.
 */
@Value
@Builder
public class Cp2020StatsView {

    List<StatLine> stats;

    /** Body Type Modifier */
    int btm;
    /** Порог спасбросков (оглушение/смерть) без штрафов = BODY */
    int save;
    /** Бег, метров за ход = MA × 3 */
    int run;
    /** Прыжок, метров = бег / 4 */
    int leap;
    /** Переносимый вес, кг = BODY × 10 */
    int carry;
    /** Поднять над головой / рывок, кг = BODY × 40 */
    int lift;

    /** Текущая человечность = EMP × 10 − потеря */
    int humanity;
    int maxHumanity;
    int humanityLoss;

    @Value
    public static class StatLine {
        String key;
        String name;
        int base;
        /** С учётом штрафов (раны для REF/INT/COOL, человечность для EMP) */
        int effective;

        public boolean isReduced() {
            return effective < base;
        }
    }
}
