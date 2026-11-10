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
    /**
     * Спасброски, которые мастер может попросить у игрока (A-07): код для {@code HealthCounterService.save} и подпись.
     */
    default List<app.charka.item.ItemField.Option> getRequestableSaves() {
        return List.of();
    }

    /** A-12: что печатать на бумажном листе сверх общего; пусто — только общее */
    default PrintSheet getPrintSheet(Character character) {
        return PrintSheet.EMPTY;
    }

    /** A-11: справка правил для панели «📖 Правила»; пусто — у системы справки нет */
    default List<Reference> getReference() {
        return List.of();
    }

    /**
     * A-10: заработок персонажа, если он работал {@code days} дней; null — у системы нет работы или у персонажа нет роли.
     */
    default Earning earnings(Character character, int days) {
        return null;
    }

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

    /**
     * UX-14: тип предмета «оружие» — вкладка окна «Добавить», которую открывает «＋ Оружие» на пустой карточке оружия;
     * null — открыть последнюю вкладку.
     */
    default String getWeaponItemType() {
        return null;
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
    default app.charka.map.MapRules getMapRules() {
        return app.charka.map.MapRules.GENERIC;
    }

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
     * То же для этого участника: у него может быть своё, например, имплант.
     */
    default List<InitiativeDeclaration> getInitiativeDeclarations(Character character) {
        return getInitiativeDeclarations();
    }

    /**
     * Действие блока вживлённого: {@link InstalledBlock#actions()}. Вызывать в транзакции.
     *
     * @return итог для показа игроку
     * @throws IllegalArgumentException если действие сейчас невозможно
     */
    default String performInstalledAction(Character character, String action, Map<String, String> params) {
        throw new IllegalArgumentException("Такого действия нет: " + action);
    }

    /**
     * Что вживлено в персонажа; null — у системы такого нет.
     */
    default InstalledBlock getInstalled(Character character) {
        return null;
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
    default List<String> onTimePassed(Campaign campaign, int days) {
        return List.of();
    }

    /**
     * Уровень навыка, заданный не персонажем (CP2020: вставленный чип). Пусто — действует природный уровень.
     */
    default java.util.Optional<app.charka.skill.SkillOverride> getSkillOverride(Character character, String skillCode) {
        return java.util.Optional.empty();
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
     * Свойства предметов, в которых хранится id другого предмета (программа в деке): при импорте кампании их переводят на новые id.
     */
    default java.util.Set<String> getItemReferenceKeys() {
        return java.util.Set.of();
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

    /**
     * Типичные регулярные траты (еда, жильё) для формы на листе. Пусто — только своя трата.
     */
    default List<ExpenseTemplate> getExpenseTemplates() {
        return List.of();
    }

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

    /** Слагаемое броска для потраченной удачи */
    static app.charka.check.CheckRequest.Part luckPart(int points) {
        return new app.charka.check.CheckRequest.Part("Удача", points);
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

    /**
     * KF-09: проверка вне боя — из чата, «Кубиков» и запроса мастера. Обычно та же, что у навыков и атак; у Космозэков —
     * 1к12 против сложности, а попадание — к100.
     */
    default CheckResolver getTableCheckResolver() {
        return getCheckResolver();
    }
}
