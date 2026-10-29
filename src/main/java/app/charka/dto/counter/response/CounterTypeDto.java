package app.charka.dto.counter.response;

/**
 * Тип счётчика для формы создания.
 *
 * @param createFieldsTemplate шаблон с фрагментом create-fields или null
 * @param usesInitialValue     показывать ли поле «Начальное значение»
 */
public record CounterTypeDto(String type, String displayName, String createFieldsTemplate,
                             boolean usesInitialValue) {
}
