package app.charka.skill;

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
 * Навыки, роли, прокачка и текущие статы
 * <p>
 * ARC-01: часть правил системы ({@code GameSystem#skills()}); каждый метод по умолчанию — «у системы этого нет».
 */
public interface SkillRules {

    /** Система без этой части */
    SkillRules NONE = new SkillRules() {
    };

    default List<SkillDefinition> getSkillCatalog() {
        return List.of();
    }

    /**
     * Роли/классы персонажей.
     */
    default List<CharacterRole> getRoles() {
        return List.of();
    }

    default Optional<CharacterRole> findRole(String code) {
        return getRoles().stream().filter(role -> role.code().equals(code)).findFirst();
    }

    /**
     * Роли персонажа в порядке каталога системы. Вызывать в транзакции.
     */
    default List<CharacterRole> rolesOf(Character character) {
        return getRoles().stream().filter(role -> character.getRoles().contains(role.code())).toList();
    }

    /**
     * Уровень спецспособности, нужный текущим ролям, чтобы взять новую; 0 — без ограничений.
     */
    default int getRoleUnlockLevel() {
        return 0;
    }

    /**
     * Прокачка навыков за очки; null — у системы её нет.
     */
    default SkillProgression getSkillProgression() {
        return null;
    }

    /**
     * Текущие статы персонажа по коду — с ними складываются навыки. Вызывать в транзакции (читает счётчики).
     */
    default Map<String, StatValue> getCurrentStats(Character character) {
        return Map.of();
    }

    /**
     * Прибавки к проверке навыка сверх стата и уровня — от других навыков и ролей персонажа. Вызывать в транзакции.
     *
     * @param skill строка навыка; null, если её нет
     */
    default List<CheckRequest.Part> getSkillBonuses(Character character, SkillDefinition definition, CharacterSkill skill) {
        return List.of();
    }

    /**
     * Уровень навыка, заданный не персонажем (CP2020: вставленный чип). Пусто — действует природный уровень.
     */
    default java.util.Optional<app.charka.skill.SkillOverride> getSkillOverride(Character character, String skillCode) {
        return java.util.Optional.empty();
    }
}
