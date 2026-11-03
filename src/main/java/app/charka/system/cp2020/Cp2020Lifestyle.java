package app.charka.system.cp2020;

import app.charka.system.ExpenseTemplate;
import app.charka.system.ExpenseTemplate.Multiplier;

import java.util.List;

/**
 * Образ жизни (с. 68, таблица «Lifestyle», «Groceries», «Housing»; прочитана с изображения страницы).
 * Жильё умножается на зону города. Месяц считается за 30 дней.
 */
final class Cp2020Lifestyle {

    private static final int WEEK = 7;
    private static final int MONTH = 30;
    private static final String FOOD = "Еда";
    private static final String HOUSING = "Жильё";
    private static final String SERVICES = "Услуги";

    /** † «Multiply base cost by location» */
    private static final List<Multiplier> ZONES = List.of(
            new Multiplier("Combat Zone", 1),
            new Multiplier("Moderate Zone", 2),
            new Multiplier("Corporate Zone", 4),
            new Multiplier("Executive Zone", 6));

    static final List<ExpenseTemplate> TEMPLATES = List.of(
            new ExpenseTemplate(FOOD, "Kibble", 50, WEEK),
            new ExpenseTemplate(FOOD, "Generic Prepak", 150, WEEK),
            new ExpenseTemplate(FOOD, "Good Prepak", 200, WEEK),
            new ExpenseTemplate(FOOD, "Fresh Food", 300, WEEK),
            new ExpenseTemplate(HOUSING, "Coffin", 20, 1, null, ZONES),
            new ExpenseTemplate(HOUSING, "Hotel Room", 100, 1, null, ZONES),
            new ExpenseTemplate(HOUSING, "Apt./Condo", 200, MONTH, "комн.", ZONES),
            new ExpenseTemplate(HOUSING, "House", 150, MONTH, "комн.", ZONES),
            new ExpenseTemplate(HOUSING, "Utilities", 100, MONTH),
            new ExpenseTemplate(SERVICES, "Cell Phone", 100, MONTH),
            new ExpenseTemplate(SERVICES, "Standard Phone", 30, MONTH),
            new ExpenseTemplate(SERVICES, "CredChip Account", 20, MONTH),
            new ExpenseTemplate(SERVICES, "Health Plan", 1000, MONTH),
            new ExpenseTemplate(SERVICES, "Trauma Team Acct.", 500, MONTH),
            new ExpenseTemplate(SERVICES, "Cable TV", 40, MONTH));

    private Cp2020Lifestyle() {
    }
}
