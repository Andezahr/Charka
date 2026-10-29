package app.charka.system;

/**
 * Валюта игровой системы.
 *
 * @param name   полное название («Евродоллары»)
 * @param symbol краткое обозначение для сумм («eb»)
 */
public record Currency(String name, String symbol) {
}
