package app.charka.model.counters.strategy;

import app.charka.model.counters.Counter;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CounterStrategy {
    String getCounterType();
    String getNewValue(String currentValue, Object inputValue);
    String  getResetValue();
    boolean canHandle(String counterType);

    /**
     * Коды игровых систем, в кампаниях которых доступен этот тип. Пустой набор — универсальный тип.
     */
    default Set<String> getSystems() {
        return Set.of();
    }

    /**
     * Доступен ли тип в кампании системы {@code systemCode}.
     */
    default boolean isAvailableIn(String systemCode) {
        return getSystems().isEmpty() || getSystems().contains(systemCode);
    }

    /**
     * Название типа для формы создания счётчика.
     */
    default String getDisplayName() {
        return getCounterType();
    }

    /**
     * Имя счётчика, если он создаётся автоматически как обязательный для другого.
     */
    default String getDefaultName() {
        return getDisplayName();
    }

    /**
     * Шаблон Thymeleaf, в котором лежит фрагмент {@code card} — карточка счётчика этого типа.
     * Новый тип со своей разметкой просто указывает свой шаблон, list.html менять не нужно.
     */
    default String getViewTemplate() {
        return "counters/types/simple";
    }

    /**
     * Шаблон с фрагментом {@code create-fields} — дополнительные поля формы создания
     * (попадают в {@code attributes[...]}). null — дополнительных полей нет.
     */
    default String getCreateFieldsTemplate() {
        return null;
    }

    /**
     * Нужно ли поле «Начальное значение» в форме создания.
     */
    default boolean usesInitialValue() {
        return true;
    }

    /**
     * Типы счётчиков, без которых этот не работает. При создании недостающие создаются автоматически,
     * удалить обязательный счётчик, пока от него кто-то зависит, нельзя.
     */
    default List<String> getRequiredCounterTypes() {
        return List.of();
    }

    /**
     * true — у персонажа может быть только один счётчик этого типа.
     */
    default boolean isUniquePerCharacter() {
        return false;
    }

    /**
     * Порядок на странице: меньше — выше.
     */
    default int getDisplayOrder() {
        return 100;
    }

    /**
     * Первичная настройка только что созданного счётчика (value уже выставлен из формы).
     * Параметры общие для всех создаваемых вместе счётчиков, поэтому незнакомые ключи нужно пропускать.
     *
     * @param params дополнительные параметры из формы создания, может быть пустым
     */
    default void initialize(Counter counter, Map<String, Integer> params) {
    }

    /**
     * Полный сброс счётчика. Стратегии со своим состоянием в attributes переопределяют его.
     */
    default void reset(Counter counter) {
        counter.setValue(Integer.valueOf(getResetValue()));
    }

    /**
     * Данные для карточки (кладутся в CounterResponseDto.view). null — карточке хватает value.
     */
    default Object describe(CounterContext context) {
        return null;
    }

    /**
     * Изменение attributes из формы редактирования; незнакомые ключи — ошибка.
     */
    default void updateAttributes(Counter counter, Map<String, Integer> attributes) {
        throw new UnsupportedOperationException("Счётчик типа " + getCounterType() + " не редактирует характеристики");
    }

    /**
     * Особое действие типа (например, ремонт брони).
     *
     * @return сообщение для пользователя
     */
    default String performAction(CounterContext context, String action) {
        throw new UnsupportedOperationException("Неизвестное действие: " + action);
    }
}
