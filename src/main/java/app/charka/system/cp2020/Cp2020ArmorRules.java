package app.charka.system.cp2020;

import java.util.List;

/**
 * Таблицы и числа брони (FNFF, с. 101–102): пропорциональная броня, предел слоёв, штраф за слои.
 */
public final class Cp2020ArmorRules {

    public static final int MAX_LAYERS = 3;
    public static final int MAX_HARD_LAYERS = 1;

    private Cp2020ArmorRules() {
    }

    /**
     * Прибавка к большему SP по таблице пропорциональной брони (с. 102).
     *
     * @param difference разница SP двух слоёв, не меньше 0
     */
    public static int proportionalBonus(int difference) {
        if (difference <= 4) return 5;
        if (difference <= 8) return 4;
        if (difference <= 14) return 3;
        if (difference <= 20) return 2;
        if (difference <= 26) return 1;
        return 0;
    }

    /**
     * Общий SP слоёв. Книга: три и больше считаются парами изнутри наружу, больший из пары с прибавкой сравнивается со следующим слоем.
     *
     * @param insideOut SP слоёв от внутреннего к внешнему; слои с SP 0 не считаются
     */
    public static int combine(List<Integer> insideOut) {
        int total = 0;
        for (int sp : insideOut) {
            if (sp <= 0) {
                continue;
            }
            total = total == 0 ? sp : Math.max(total, sp) + proportionalBonus(Math.abs(total - sp));
        }
        return total;
    }

    /**
     * Лишний штраф к REF за слои (с. 101): второй слой −1, третий ещё −2.
     */
    public static int layerPenalty(int layers) {
        if (layers < 2) return 0;
        return layers == 2 ? 1 : 3;
    }
}
