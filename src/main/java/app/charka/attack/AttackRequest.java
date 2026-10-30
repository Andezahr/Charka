package app.charka.attack;

import java.util.Set;

/**
 * Параметры атаки из формы.
 *
 * @param targetId   цель с листом из той же кампании; null — цель без листа
 * @param targetName имя цели без листа
 * @param distance   расстояние до цели, м
 * @param location   код выбранной зоны; пусто — случайная
 * @param skillCode  навык атаки, если у оружия есть выбор
 * @param options    коды выбранных модификаторов
 * @param modifier   прочий модификатор
 */
public record AttackRequest(Long targetId, String targetName, int distance, String location, String skillCode,
                            Set<String> options, int modifier) {

    public AttackRequest {
        options = Set.copyOf(options);
    }

    public boolean hasChosenLocation() {
        return location != null && !location.isBlank();
    }
}
