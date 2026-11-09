package app.charka.system.kosmozeki;

import app.charka.attack.AttackRules;
import app.charka.check.CheckResolver;
import app.charka.encounter.CombatCard;
import app.charka.encounter.Initiative;
import app.charka.item.CatalogItem;
import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemSummary;
import app.charka.item.ItemTypeDefinition;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.InspirationCounterStrategy;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.npc.NpcTemplate;
import app.charka.service.counter.HealthCounterService;
import app.charka.skill.CharacterRole;
import app.charka.system.Currency;
import app.charka.system.GameSystem;
import app.charka.system.ProfileField;
import app.charka.system.Reference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Космозэки — хоумбрю-система друзей автора (правила — {@code RULES_KOSMOZEKI.md}): к100, зоны тела со своим ХП,
 * броня из каждой пули, за ход одно действие и перемещение. Чисел у персонажа нет — теги, приговор, качества.
 */
@Component
@RequiredArgsConstructor
public class KzSystem implements GameSystem {

    public static final String CODE = "KOSMOZEKI";

    private static final Currency CURRENCY = new Currency("Черкаши", "ч.");

    private static final CheckResolver CHECK_RESOLVER = new KzCheckResolver();
    private static final CheckResolver TABLE_CHECK_RESOLVER = new KzTableCheckResolver();

    private static final AttackRules ATTACK_RULES = new KzAttackRules();

    /** Функциональные роли из документа: польза в команде, без уровней */
    static final List<CharacterRole> ROLES = List.of(
            role("SHOOTER", "Стрелок / Воитель"),
            role("MECHANIC", "Механик / Инженер"),
            role("LEADER", "Парламентёр / Лидер"),
            role("TRADER", "Бизнесмен / Торговец"),
            role("PILOT", "Водила / Пилот"),
            role("MEDIC", "Медик / Биоинженер"),
            role("POACHER", "Браконьер / Биолог"),
            role("THIEF", "Проныра / Ворюга"));

    private final HealthCounterService healthCounterService;
    private final DiceRoller dice;

    private static CharacterRole role(String code, String name) {
        return new CharacterRole(code, name, null);
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "Космозэки";
    }

    @Override
    public Currency getCurrency() {
        return CURRENCY;
    }

    @Override
    public List<String> getDefaultCounterTypes() {
        return List.of(KzBodyStrategy.TYPE, InspirationCounterStrategy.TYPE);
    }

    @Override
    public List<String> getQuickDice() {
        return List.of("d100", "d12", "d10", "d6");
    }

    @Override
    public CheckResolver getCheckResolver() {
        return CHECK_RESOLVER;
    }

    /** KF-09: вне боя — 1к12 против сложности, которую называет мастер */
    @Override
    public CheckResolver getTableCheckResolver() {
        return TABLE_CHECK_RESOLVER;
    }

    @Override
    public List<CharacterRole> getRoles() {
        return ROLES;
    }

    @Override
    public List<ItemTypeDefinition> getItemTypes() {
        return KzItems.ALL;
    }

    @Override
    public List<CatalogItem> getItemCatalog() {
        return KzItems.CATALOG;
    }

    @Override
    public List<ItemAction> getItemActions(Item item) {
        return KzItemActions.actions(item);
    }

    @Override
    public String performItemAction(Item item, String action, Map<String, String> params) {
        return KzItemActions.perform(item, action, params);
    }

    @Override
    public ItemSummary getItemSummary(Item item) {
        ItemSummary summary = KzItemActions.summary(item);
        return summary != null ? summary : GameSystem.super.getItemSummary(item);
    }

    @Override
    public ItemAttachments getItemAttachments(Item item) {
        return KzItemActions.attachments(item);
    }

    @Override
    public AttackRules getAttackRules() {
        return ATTACK_RULES;
    }

    /** Инициатива — 1к12 раз за бой; отложенный кубик предсказания заменяет бросок */
    @Override
    public Initiative rollInitiative(Character character, DiceRoller dice) {
        List<String> steps = new ArrayList<>();
        int roll = KzAbilities.d12(character, dice, steps, "к12");
        return new Initiative(roll, steps.get(0).replace("к12: ", ""));
    }

    @Override
    public boolean rollsInitiativeEachRound() {
        return false;
    }

    /** За ход — действие и перемещение: бег действие не тратит */
    @Override
    public boolean movementIsAction() {
        return false;
    }

    /** За ход — одно действие (RULES_KOSMOZEKI.md, раздел 2): вторая атака, перезарядка или лечение — уже в следующем раунде */
    @Override
    public int actionsPerRound() {
        return 1;
    }

    /** Новый бой: способности «раз за бой» восстанавливаются */
    @Override
    public void onEncounterStart(Character character) {
        KzAbilities.restoreForFight(character);
    }

    @Override
    public String getHealthStatus(Character character) {
        return KzAbilities.bodyCounter(character).map(KzSystem::healthBrief).orElse(null);
    }

    private static String healthBrief(Counter body) {
        String status = KzBodyStrategy.statusLabel(body);
        KzBody zones = KzBodyStrategy.body(body);
        List<String> hurt = zones.zones().stream()
                .filter(zone -> KzBodyStrategy.hp(body, zone) < zone.maxHp())
                .map(zone -> zone.name() + " " + KzBodyStrategy.hp(body, zone))
                .toList();
        return hurt.isEmpty() ? status : status + " · " + String.join(", ", hurt);
    }

    @Override
    public List<String> getCharacterBrief(Character character) {
        List<String> brief = new ArrayList<>();
        String tags = KzProfile.tags(character);
        if (tags != null) {
            brief.add(tags);
        }
        brief.addAll(GameSystem.super.getCharacterBrief(character));
        KzAbilities.bodyCounter(character).ifPresent(body -> brief.add(healthBrief(body)));
        character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(KzItems::isWeapon)
                .map(Item::getName)
                .forEach(brief::add);
        return brief;
    }

    @Override
    public CombatCard getCombatCard(Character character) {
        Optional<Counter> found = KzAbilities.bodyCounter(character);
        if (found.isEmpty()) {
            return GameSystem.super.getCombatCard(character);
        }
        Counter body = found.get();
        KzBody zones = KzBodyStrategy.body(body);
        List<String> lines = new ArrayList<>();
        for (KzBody.Zone zone : zones.zones()) {
            int hp = KzBodyStrategy.hp(body, zone);
            int armor = KzBodyStrategy.armor(body, zone);
            if (hp < zone.maxHp() || armor > 0) {
                lines.add(zone.name() + " " + hp + "/" + zone.maxHp() + (armor > 0 ? " 🛡" + armor : ""));
            }
        }
        boolean out = KzBodyStrategy.out(body);
        return new CombatCard(body.getId(), KzBodyStrategy.statusLabel(body), out, out, lines, List.of(), false,
                zones.zones().stream().map(zone -> new CombatCard.Option(zone.code(), zone.name())).toList(), List.of());
    }

    /**
     * Промотка времени: отдых лечит каждую зону (сон +30, сон и еда +60 в день), после сна предсказание бросается заново.
     */
    @Override
    public List<String> onTimePassed(Campaign campaign, int days) {
        List<String> report = new ArrayList<>(Optional.ofNullable(healthCounterService.healOverTime(campaign.getId(), days)).orElse(List.of()));
        if (days > 0 && campaign.getCharacters() != null) {
            campaign.getCharacters().forEach(character -> KzAbilities.counter(character, KzPortentStrategy.TYPE).ifPresent(portent -> {
                KzPortentStrategy.roll(portent, dice);
                report.add(character.getName() + ": предсказание " + KzPortentStrategy.dice(portent).values());
            }));
        }
        return report;
    }

    @Override
    public List<NpcTemplate> getNpcTemplates() {
        return KzNpcs.templates();
    }

    @Override
    public app.charka.npc.NpcSheet rollNpc(String templateCode, boolean advanced, DiceRoller dice) {
        return KzNpcs.sheet(templateCode);
    }

    @Override
    public List<ProfileField> getProfileFields() {
        return KzProfile.FIELDS;
    }

    @Override
    public List<Reference> getReference() {
        return KzReference.sections();
    }

    /** Копия NPC получает полный магазин */
    @Override
    public void prepareItemCopy(Item item) {
        if (KzItems.isRanged(item) && item.getProperties().containsKey(KzItems.CAPACITY)) {
            item.getProperties().put(KzItems.LOADED, item.getProperties().get(KzItems.CAPACITY));
        }
    }
}
