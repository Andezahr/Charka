package app.charka.encounter;

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
 * Бой: инициатива, карточка участника, противостояния, действия за раунд
 * <p>
 * ARC-01: часть правил системы ({@code GameSystem#combat()}); каждый метод по умолчанию — «у системы этого нет».
 */
public interface CombatRules {

    /** Система без этой части */
    CombatRules NONE = new CombatRules() {
    };

    /**
     * Что можно объявить в начале раунда боя.
     */
    default List<InitiativeDeclaration> getInitiativeDeclarations() {
        return List.of();
    }

    /**
     * То же для этого участника: у него может быть своё, например, имплант.
     */
    default List<InitiativeDeclaration> getInitiativeDeclarations(Character character) {
        return getInitiativeDeclarations();
    }

    /**
     * Инициатива участника боя; по умолчанию d20. Вызывать в транзакции.
     */
    default Initiative rollInitiative(Character character, DiceRoller dice) {
        int roll = dice.roll(20);
        return new Initiative(roll, "d20 = " + roll);
    }

    /**
     * Очередь бросается заново каждый раунд; иначе один раз за бой. Это значение по умолчанию для новой кампании:
     * дальше решает {@code Campaign.initiativeEachRound} (A-13).
     */
    default boolean rollsInitiativeEachRound() {
        return false;
    }

    /**
     * Что показать об участнике в строке боевого трекера. Вызывать в транзакции.
     */
    default CombatCard getCombatCard(Character character) {
        return CombatCard.brief(List.of());
    }

    /**
     * Противостояния в трекере боя: участник против другого участника. Пусто — кнопок нет.
     */
    default List<app.charka.encounter.CombatContest> getCombatContests() {
        return List.of();
    }

    /**
     * Из чего складывается бросок участника в противостоянии.
     *
     * @throws IllegalArgumentException если такого противостояния нет
     */
    default app.charka.check.CheckRequest getContestRequest(String code, Character character) {
        throw new IllegalArgumentException("Такого противостояния нет: " + code);
    }

    /**
     * Что остаётся на проигравшем до конца боя или пока мастер не снимет: название эффекта; null — ничего.
     */
    default String getContestLoserEffect(String code, Character winner) {
        return null;
    }

    /**
     * K-07: новый бой — способности «раз за бой» восстанавливаются. Вызывать в транзакции.
     */
    default void onEncounterStart(Character character) {
    }

    /**
     * K-07: тратит ли бег по карте действие раунда (CP2020 — да, бег — действие; Космозэки — нет: действие и перемещение).
     */
    default boolean movementIsAction() {
        return true;
    }

    /**
     * Сколько действий участник может сделать за раунд боя (атака, перезарядка, медпомощь, проверка). 0 — без предела:
     * CP2020 разрешает несколько, каждое следующее со штрафом ({@code AttackRules.actionOption}); Космозэки — одно.
     */
    default int actionsPerRound() {
        return 0;
    }

    /**
     * Навыки, нужные в бою почти всегда: в проверке из строки участника они первые, даже на уровне 0.
     */
    default List<String> getCombatCheckSkills() {
        return List.of();
    }
}
