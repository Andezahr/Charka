package app.charka.system;

import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.check.GenericCheckResolver;
import app.charka.item.CatalogItem;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.CharacterSkill;
import app.charka.skill.CharacterRole;
import app.charka.skill.SkillDefinition;
import app.charka.skill.SkillProgression;
import app.charka.skill.StatValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Игровая система (НРИ): правила, которые различаются между CP2020, D&D и т.п.
 * Ядро обращается к правилам только через этот интерфейс, реализации находит {@link GameSystemRegistry}.
 */
public interface GameSystem {

    CheckResolver GENERIC_CHECK = new GenericCheckResolver();

    /**
     * Код системы, хранится в {@code Campaign.system} («CP2020», «GENERIC»).
     */
    String getCode();

    /**
     * Название для интерфейса.
     */
    String getDisplayName();

    Currency getCurrency();

    /**
     * Типы счётчиков, создаваемых вместе с новым персонажем кампании этой системы.
     */
    default List<String> getDefaultCounterTypes() {
        return List.of();
    }

    /**
     * Выражения для быстрых кнопок броска ({@code DiceExpression}).
     */
    default List<String> getQuickDice() {
        return List.of("d4", "d6", "d8", "d10", "d12", "d20", "d100");
    }

    /**
     * Правила проверки «бросок + модификатор против сложности». По умолчанию — d20 без критов.
     */
    default CheckResolver getCheckResolver() {
        return GENERIC_CHECK;
    }

    /**
     * Каталог навыков; пусто — у системы нет навыков и вкладка «Навыки» не показывается.
     */
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
     * Типы предметов системы; обычный предмет есть всегда.
     */
    default List<ItemTypeDefinition> getItemTypes() {
        return List.of(ItemTypeDefinition.GENERIC);
    }

    default Optional<ItemTypeDefinition> findItemType(String code) {
        return getItemTypes().stream().filter(type -> type.code().equals(code)).findFirst();
    }

    /**
     * Справочник готовых предметов; пусто — у системы его нет.
     */
    default List<CatalogItem> getItemCatalog() {
        return List.of();
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
     * Вызывается после промотки времени кампании (лечение, траты по правилам системы).
     */
    default void onTimePassed(Campaign campaign, int days) {
    }
}
