package app.charka.attack;

import java.util.List;
import java.util.Map;

/**
 * Что попадание оставляет на цели на следующие раунды боя: горение, слепота.
 *
 * @param label        что показать у участника: «Горит», «Ослеплён»
 * @param rounds       сколько раундов ещё длится, считая со следующего
 * @param damage       броски урона на следующие раунды, по одному на раунд; пусто — урона нет
 * @param data         данные системы для урона: ядро их только хранит
 * @param attackOption какой модификатор атаки предложить этому участнику; null — никакого
 * @param onAttacker   эффект на том, кто атаковал, а не на цели
 */
public record LingeringEffect(String label, int rounds, List<String> damage, Map<String, String> data, String attackOption, boolean onAttacker) {

    public LingeringEffect(String label, int rounds, List<String> damage, Map<String, String> data, String attackOption) {
        this(label, rounds, damage, data, attackOption, false);
    }

    public LingeringEffect {
        damage = List.copyOf(damage);
        data = Map.copyOf(data);
    }

    /** Для мастера, когда учёта раундов нет: цель не в бою */
    public String describe() {
        return label + ": " + (damage.isEmpty() ? "раундов " + rounds : "ещё " + String.join(", ", damage));
    }
}
