package app.charka.system;

import app.charka.attack.AttackRules;
import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.check.GenericCheckResolver;
import app.charka.encounter.CombatCard;
import app.charka.encounter.Initiative;
import app.charka.encounter.InitiativeDeclaration;
import app.charka.item.InstalledBlock;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.model.Item;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcSheet;
import app.charka.npc.NpcTemplate;
import app.charka.skill.CharacterRole;
import app.charka.skill.SkillDefinition;
import app.charka.skill.SkillProgression;
import app.charka.skill.StatValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
