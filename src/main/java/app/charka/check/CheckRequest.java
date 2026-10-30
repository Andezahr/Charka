package app.charka.check;

import java.util.List;

/**
 * Запрос проверки: бросок системы + модификатор против сложности.
 *
 * @param label      подпись («Взлом», «Атлетика»), может быть null
 * @param modifier   сумма модификаторов (стат + навык + ситуативные)
 * @param difficulty сложность (DC); null — просто узнать итог, без успеха/провала
 * @param parts      из чего сложился модификатор — для шагов расчёта; пусто — одно число
 * @param skillCode  код проверяемого навыка, null — свободная проверка (нужен, например, для таблиц фамблов)
 */
public record CheckRequest(String label, int modifier, Integer difficulty, List<Part> parts, String skillCode) {

    /**
     * Слагаемое модификатора: «REF +8».
     */
    public record Part(String name, int value) {
    }

    public CheckRequest {
        parts = List.copyOf(parts);
    }

    public CheckRequest(String label, int modifier, Integer difficulty) {
        this(label, modifier, difficulty, List.of(), null);
    }

    public static CheckRequest ofParts(String label, List<Part> parts, Integer difficulty, String skillCode) {
        int modifier = parts.stream().mapToInt(Part::value).sum();
        return new CheckRequest(label, modifier, difficulty, parts, skillCode);
    }
}
