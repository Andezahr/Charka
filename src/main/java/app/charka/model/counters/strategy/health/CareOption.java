package app.charka.model.counters.strategy.health;

/**
 * Медпомощь, которую оказывает персонаж своим навыком: стабилизация, лечение.
 *
 * @param code          код для формы
 * @param label         подпись
 * @param skillCode     навык медика из каталога системы
 * @param selfAllowed   можно ли самому себе
 * @param stabilization это стабилизация умирающего: предлагается, только пока она нужна
 */
public record CareOption(String code, String label, String skillCode, boolean selfAllowed, boolean stabilization) {
}
