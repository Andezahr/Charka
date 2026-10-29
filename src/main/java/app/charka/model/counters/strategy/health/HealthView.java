package app.charka.model.counters.strategy.health;

/**
 * Состояние здоровья для отображения. Общая часть, которую можно показать для любой системы;
 * конкретная реализация добавляет свои поля, а её шаблон карточки их использует.
 */
public interface HealthView {

    /** Сколько урона получено */
    int getDamage();

    /** Порог, после которого персонаж погибает */
    int getMaxDamage();

    /** Текущее состояние словами: «Серьёзное ранение», «Мёртв»… */
    String getStatusLabel();

    boolean isDead();
}
