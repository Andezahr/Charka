package app.charka.attack;

/**
 * Предмет, который можно выбрать целью атаки: машина.
 *
 * @param value   значение в списке целей формы: «item-42»
 * @param ownerId чей предмет: свою машину атакующему не предлагают
 * @param label   название, владелец и прочность: «Седан · Олежа · SDP 26/30»
 */
public record ObjectTarget(String value, Long ownerId, String label) {
}
