package app.charka.check;

/**
 * Запрос проверки: бросок системы + модификатор против сложности.
 * Навыки и статы (этап 3) будут складываться в {@code modifier} до вызова резолвера.
 *
 * @param label      подпись («Взлом», «Атлетика»), может быть null
 * @param modifier   сумма модификаторов (стат + навык + ситуативные)
 * @param difficulty сложность (DC); null — просто узнать итог, без успеха/провала
 */
public record CheckRequest(String label, int modifier, Integer difficulty) {
}
