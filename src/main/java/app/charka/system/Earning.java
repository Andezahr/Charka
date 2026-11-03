package app.charka.system;

/**
 * Заработок персонажа за прошедшие дни (A-10): сколько и за что — запись в деньгах.
 *
 * @param amount  сумма за период
 * @param monthly ставка за месяц, для подсказки в форме
 * @param label   «Работа: Соло, спецспособность 6»
 */
public record Earning(long amount, long monthly, String label) {
}
