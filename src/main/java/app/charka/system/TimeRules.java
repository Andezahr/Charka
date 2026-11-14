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
 * Время: что происходит за прошедшие дни, заработок, шаблоны регулярных трат и временных эффектов
 * <p>
 * ARC-01: часть правил системы ({@code GameSystem#time()}); каждый метод по умолчанию — «у системы этого нет».
 */
public interface TimeRules {

    /** Система без этой части */
    TimeRules NONE = new TimeRules() {
    };

    /**
     * Вызывается после промотки времени кампании (лечение, траты по правилам системы).
     */
    default List<String> onTimePassed(Campaign campaign, int days) {
        return List.of();
    }

    /**
     * A-10: заработок персонажа, если он работал {@code days} дней; null — у системы нет работы или у персонажа нет роли.
     */
    default Earning earnings(Character character, int days) {
        return null;
    }

    /**
     * Типичные регулярные траты (еда, жильё) для формы на листе. Пусто — только своя трата.
     */
    default List<ExpenseTemplate> getExpenseTemplates() {
        return List.of();
    }

    /**
     * Готовые временные эффекты (препараты, стимуляторы) для блока «Эффекты» на листе. Пусто — только свой эффект.
     */
    default List<app.charka.effect.EffectTemplate> getEffectTemplates() {
        return List.of();
    }

    default Optional<app.charka.effect.EffectTemplate> findEffectTemplate(String code) {
        return getEffectTemplates().stream().filter(template -> template.code().equals(code)).findFirst();
    }
}
