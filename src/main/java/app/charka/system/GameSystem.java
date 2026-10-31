package app.charka.system;

import app.charka.attack.AttackRules;
import app.charka.check.CheckRequest;
import app.charka.check.CheckResolver;
import app.charka.check.GenericCheckResolver;
import app.charka.encounter.CombatCard;
import app.charka.encounter.Initiative;
import app.charka.encounter.InitiativeDeclaration;
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
     * Действия над предметом на листе персонажа; пусто — у предмета их нет. Вызывать в транзакции.
     */
    default List<ItemAction> getItemActions(Item item) {
        return List.of();
    }

    /**
     * Краткая строка предмета на листе. Вызывать в транзакции.
     */
    default ItemSummary getItemSummary(Item item) {
        return findItemType(item.getType())
                .map(type -> ItemSummary.allFields(type, item.getProperties()))
                .orElse(new ItemSummary(null, List.of()));
    }

    /**
     * Что показывается вместе с предметом, а не своими строками. Вызывать в транзакции.
     */
    default ItemAttachments getItemAttachments(Item item) {
        return ItemAttachments.NONE;
    }

    /**
     * Строки справочника, которые этому персонажу нужны в первую очередь: патроны и магазины к его оружию.
     */
    default List<CatalogItem> getSuggestedCatalog(Character character) {
        return List.of();
    }

    /**
     * Что сейчас важно знать о предмете сверх его полей; показывается в строке предмета.
     */
    default List<String> getItemNotes(Item item) {
        return List.of();
    }

    /**
     * Выполнить действие над предметом. Вызывать в транзакции.
     *
     * @param params параметры формы действия ({@code source}, {@code amount})
     * @return итог для показа игроку
     * @throws IllegalArgumentException если действие сейчас невозможно
     */
    default String performItemAction(Item item, String action, Map<String, String> params) {
        throw new IllegalArgumentException("Неизвестное действие с предметом: " + action);
    }

    /**
     * Правила атаки оружием; null — у системы их нет.
     */
    default AttackRules getAttackRules() {
        return null;
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
     * Коротко о персонаже для списка кампании: мастеру не нужно открывать лист. Вызывать в транзакции.
     */
    default List<String> getCharacterBrief(Character character) {
        return rolesOf(character).stream().map(CharacterRole::name).toList();
    }

    /**
     * Что можно объявить в начале раунда боя.
     */
    default List<InitiativeDeclaration> getInitiativeDeclarations() {
        return List.of();
    }

    /**
     * Навыки, нужные в бою почти всегда: в проверке из строки участника они первые, даже на уровне 0.
     */
    default List<String> getCombatCheckSkills() {
        return List.of();
    }

    /**
     * Инициатива участника боя; по умолчанию d20. Вызывать в транзакции.
     */
    default Initiative rollInitiative(Character character, DiceRoller dice) {
        int roll = dice.roll(20);
        return new Initiative(roll, "d20 = " + roll);
    }

    /**
     * Очередь бросается заново каждый раунд; иначе один раз за бой.
     */
    default boolean rollsInitiativeEachRound() {
        return false;
    }

    /**
     * Что показать об участнике в строке боевого трекера. Вызывать в транзакции.
     */
    default CombatCard getCombatCard(Character character) {
        return CombatCard.brief(getCharacterBrief(character));
    }

    /**
     * Предмет скопирован новому NPC: что не должно перейти (износ, расход), система убирает здесь.
     */
    default void prepareItemCopy(Item item) {
    }

    /**
     * Шаблоны быстрого создания NPC; пусто — только пустой NPC.
     */
    default List<NpcTemplate> getNpcTemplates() {
        return List.of();
    }

    /**
     * Выбросить NPC по шаблону.
     *
     * @param advanced продвинутый NPC, если система это различает
     * @throws IllegalArgumentException неизвестный шаблон
     */
    default NpcSheet rollNpc(String templateCode, boolean advanced, DiceRoller dice) {
        throw new IllegalArgumentException("Нет такого шаблона NPC: " + templateCode);
    }

    /**
     * Вызывается после промотки времени кампании (лечение, траты по правилам системы).
     */
    default void onTimePassed(Campaign campaign, int days) {
    }
}
