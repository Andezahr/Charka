package app.charka.check;

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
 * Удача: сколько осталось, потратить на бросок, вернуть к новой сессии
 * <p>
 * ARC-01: часть правил системы ({@code GameSystem#luck()}); каждый метод по умолчанию — «у системы этого нет».
 */
public interface LuckRules {

    /** Система без этой части */
    LuckRules NONE = new LuckRules() {
    };

    /**
     * Очки, которые игрок прибавляет к важному броску, объявив до него (CP2020: Удача, с. 26). 0 — нет.
     */
    default int getLuck(Character character) {
        return 0;
    }

    /**
     * Потратить очки на бросок.
     *
     * @throws IllegalArgumentException если столько нет
     */
    default void spendLuck(Character character, int points) {
        if (points > 0) {
            throw new IllegalArgumentException("Удачи в этой системе нет");
        }
    }

    /** Есть ли в системе удача: на странице кампании появляется «Новая сессия» */
    default boolean hasLuck() {
        return false;
    }

    /** Новая игровая сессия: потраченные очки возвращаются */
    default void restoreLuck(Character character) {
    }
}
