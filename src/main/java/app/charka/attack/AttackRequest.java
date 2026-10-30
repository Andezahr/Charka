package app.charka.attack;

import java.util.List;
import java.util.Set;

/**
 * Параметры атаки из формы.
 *
 * @param targetIds  цели с листом из той же кампании: при обычной атаке не больше одной, в зоне огня сколько вошло; пусто — цель без листа
 * @param targetName имя цели без листа
 * @param distance   расстояние до цели, м
 * @param location   код выбранной зоны; пусто — случайная
 * @param skillCode  навык атаки, если у оружия есть выбор
 * @param options    коды выбранных модификаторов
 * @param modifier   прочий модификатор; в зоне огня — итог цели без листа
 * @param mode       код режима огня; пусто — одиночный выстрел
 * @param rounds     сколько патронов выпустить, если режим это спрашивает
 * @param zoneWidth  ширина зоны огня, м
 */
public record AttackRequest(List<Long> targetIds, String targetName, int distance, String location, String skillCode,
                            Set<String> options, int modifier, String mode, int rounds, int zoneWidth) {

    public AttackRequest {
        targetIds = List.copyOf(targetIds);
        options = Set.copyOf(options);
    }

    /** Одиночный выстрел */
    public AttackRequest(Long targetId, String targetName, int distance, String location, String skillCode,
                         Set<String> options, int modifier) {
        this(targetId == null ? List.of() : List.of(targetId), targetName, distance, location, skillCode, options, modifier, "", 0, 0);
    }

    public boolean hasChosenLocation() {
        return location != null && !location.isBlank();
    }
}
