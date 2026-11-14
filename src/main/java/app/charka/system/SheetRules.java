package app.charka.system;

import app.charka.model.Character;
import java.util.List;

/**
 * Лист персонажа: поля профиля, строка здоровья, заголовок контактов, лист на бумаге
 * <p>
 * ARC-01: часть правил системы ({@code GameSystem#sheet()}); каждый метод по умолчанию — «у системы этого нет».
 */
public interface SheetRules {

    /** Система без этой части */
    SheetRules NONE = new SheetRules() {
    };

    /**
     * K-11: поля листа персонажа, которые система хранит текстом ({@code Character.profile}): теги, приговор. Пусто — нет.
     */
    default List<ProfileField> getProfileFields() {
        return List.of();
    }

    /**
     * Состояние здоровья одной строкой для шапки листа: «Серьёзное · урон 6». null — счётчика здоровья нет, на листе — число ран.
     */
    default String getHealthStatus(Character character) {
        return null;
    }

    /**
     * KF-07: как в этой системе называются люди, которых знает персонаж (карточка на странице заметок). Ядро — «Знакомые»;
     * у CP2020 — «Контакты», как в книге.
     */
    default String getContactsTitle() {
        return "Знакомые";
    }

    /** A-12: что печатать на бумажном листе сверх общего; пусто — только общее */
    default PrintSheet getPrintSheet(Character character) {
        return PrintSheet.EMPTY;
    }
}
