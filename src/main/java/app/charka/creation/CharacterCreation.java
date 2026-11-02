package app.charka.creation;

import app.charka.model.Campaign;
import app.charka.model.Character;

import java.util.List;
import java.util.Map;

/**
 * Мастер создания персонажа по правилам системы: страница, бросок очков и само создание.
 * Система, у которой его нет, создаёт персонажа по одному имени.
 */
public interface CharacterCreation {

    /** Код системы, для кампаний которой этот мастер */
    String getSystemCode();

    /** Шаблон страницы мастера */
    String getTemplate();

    /** Что нужно странице: роли, навыки, таблицы */
    Object form(Campaign campaign);

    /**
     * Бросок очков характеристик способом системы; пишется в лог бросков кампании.
     *
     * @throws IllegalArgumentException если такого способа нет
     */
    Roll roll(Campaign campaign, String method);

    /**
     * Бросок предыстории (CP2020: лайфпас): ответ странице; при создании он возвращается полем формы.
     *
     * @param age возраст; null — по правилам системы
     * @return null — у системы предыстории нет
     */
    default Object background(Campaign campaign, Integer age) {
        return null;
    }

    /**
     * Создать персонажа из полей формы.
     *
     * @return созданный персонаж
     * @throws IllegalArgumentException если ввод нарушает правила: что не так — в сообщении
     */
    Character create(Campaign campaign, Map<String, String> params);

    /**
     * @param total итог
     * @param rolls отдельные кубики, если они нужны странице (быстрый способ: по кубику на стат)
     */
    record Roll(int total, List<Integer> rolls) {
    }
}
