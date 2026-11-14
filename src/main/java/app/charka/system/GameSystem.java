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

    // ---------- ARC-01: части правил; у системы без части — пустая ----------

    /** ARC-01: бой: инициатива, карточка участника, противостояния, действия за раунд */
    default app.charka.encounter.CombatRules combat() {
        GameSystem system = this;
        // без своей части боя карточка участника — краткая строка системы (роли)
        return new app.charka.encounter.CombatRules() {
            @Override
            public CombatCard getCombatCard(Character character) {
                return CombatCard.brief(system.getCharacterBrief(character));
            }
        };
    }

    /** ARC-01: предметы: типы, справочник, действия, строка на листе, вживлённое */
    default app.charka.item.ItemRules items() {
        return app.charka.item.ItemRules.NONE;
    }

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
