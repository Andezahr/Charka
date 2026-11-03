package app.charka.system;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Типичная регулярная трата системы: выбор на листе заполняет форму. Сумма может умножаться на число (комнат)
 * и на выбор из списка (зона города).
 *
 * @param group       раздел списка: «Еда», «Жильё»
 * @param name        название траты
 * @param amount      сумма за период за одну единицу
 * @param periodDays  период в днях
 * @param unitLabel   за что умножается количеством («комнат»); null — количества нет
 * @param multipliers множитель на выбор (зона); пусто — нет
 */
public record ExpenseTemplate(String group, String name, long amount, int periodDays, String unitLabel, List<Multiplier> multipliers) {

    public ExpenseTemplate {
        multipliers = List.copyOf(multipliers);
    }

    public ExpenseTemplate(String group, String name, long amount, int periodDays) {
        this(group, name, amount, periodDays, null, List.of());
    }

    /** Для атрибута разметки: «Combat Zone:1|Moderate Zone:2» */
    public String getMultiplierText() {
        return multipliers.stream().map(multiplier -> multiplier.label() + ":" + multiplier.factor()).collect(Collectors.joining("|"));
    }

    public record Multiplier(String label, int factor) {
    }
}
