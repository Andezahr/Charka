package app.charka.system;

import org.springframework.stereotype.Component;

/**
 * Универсальная система без особых правил — только ядро.
 */
@Component
public class GenericSystem implements GameSystem {

    public static final String CODE = "GENERIC";

    private static final Currency CURRENCY = new Currency("Условные единицы", "у.е.");

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "Универсальная";
    }

    @Override
    public Currency getCurrency() {
        return CURRENCY;
    }
}
