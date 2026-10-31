package app.charka.system.cp2020;

import app.charka.item.InstalledBlock;
import app.charka.item.ItemAction;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.service.counter.HealthCounterService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Установка киберware (с. 72–75): бросок потери человечности, хирургия, что установленное даёт персонажу.
 * Установленное лежит в инвентаре предметом со свойством {@code worn}: так броня-кибер становится слоем без отдельного кода.
 */
final class Cp2020Cyber {

    static final String INSTALL = "INSTALL";
    /** Для быстрых NPC: без потери человечности и хирургии */
    static final String GRANT = "GRANT";
    static final String REMOVE = "REMOVE";
    static final String REPAIR = "REPAIR";
    static final String SPEEDWARE = "Speedware";

    private static final String CONFIRM = "Установить? Потеря человечности и урон хирургии бросаются сразу, снятие их не вернёт.";

    /**
     * Хирургия: таблица с. 72–75.
     *
     * @param damage урон операции
     * @param cost   цена операции, eb
     * @param check  сложность Medical Tech
     */
    private record Surgery(String code, String label, String damage, int cost, int check) {
    }

    private static final List<Surgery> SURGERIES = List.of(
            new Surgery("N", "Negligible", "1", 0, 10),
            new Surgery("M", "Minor", "1D6+1", 500, 15),
            new Surgery("MA", "Major", "2D6+1", 1500, 20),
            new Surgery("CR", "Critical", "3D6+1", 2500, 25));

    private Cp2020Cyber() {
    }

    static boolean isCyber(Item item) {
        return Cp2020ItemTypes.CYBER.equals(item.getType());
    }

    static boolean isInstalled(Item item) {
        return isCyber(item) && item.getProperties().containsKey(Cp2020ItemTypes.WORN);
    }

    static List<ItemAction> actions(Item item) {
        if (!isInstalled(item)) {
            return List.of(ItemAction.simple(INSTALL, "Установить", blockedReason(item)).withConfirm(CONFIRM));
        }
        List<ItemAction> actions = new ArrayList<>();
        if (Cp2020WornArmor.isDamaged(item)) {
            actions.add(ItemAction.simple(REPAIR, "Починить", null).asSecondary());
        }
        actions.add(ItemAction.simple(REMOVE, "Снять", null).asSecondary());
        return actions;
    }

    /** Почему нельзя установить; null — можно. Базовый модуль и место в слоях брони */
    private static String blockedReason(Item item) {
        String requires = item.getProperties().get(Cp2020ItemTypes.REQUIRES);
        if (requires != null) {
            List<String> parents = Arrays.asList(requires.split("\\|"));
            boolean present = installed(item.getInventory().getCharacter()).anyMatch(other -> parents.contains(other.getName()));
            if (!present) {
                return "Сначала установите: " + String.join(" или ", parents);
            }
        }
        return Cp2020WornArmor.sp(item) > 0 ? Cp2020ArmorActions.wearBlockedReason(item) : null;
    }

    /**
     * @return итог для показа игроку
     * @throws IllegalArgumentException если действие сейчас невозможно
     */
    static String perform(Item item, String action, DiceRoller dice, HealthCounterService health) {
        if (GRANT.equals(action)) {
            markInstalled(item);
            return item.getName();
        }
        if (actions(item).stream().noneMatch(known -> known.code().equals(action))) {
            throw new IllegalArgumentException("Неизвестное действие с предметом: " + action);
        }
        return switch (action) {
            case INSTALL -> install(item, dice, health);
            case REPAIR -> {
                Cp2020WornArmor.repair(item);
                yield item.getName() + ": отремонтирован, SP восстановлен";
            }
            default -> {
                item.getProperties().remove(Cp2020ItemTypes.WORN);
                yield item.getName() + ": снят, человечность не возвращается: терапия, с. 74";
            }
        };
    }

    private static String install(Item item, DiceRoller dice, HealthCounterService health) {
        String blocked = blockedReason(item);
        if (blocked != null) {
            throw new IllegalArgumentException(blocked);
        }
        Character owner = item.getInventory().getCharacter();
        Counter stats = owner.getCounters().stream()
                .filter(counter -> Cp2020StatsStrategy.TYPE.equals(counter.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("У персонажа нет счётчика характеристик: создайте его на вкладке «Счётчики»"));
        List<String> parts = new ArrayList<>();
        parts.add(item.getName() + ": установлен");
        // HOMEBREW: снятое и поставленное обратно потери не бросает: книга говорит о потере за установку нового
        if (!item.getProperties().containsKey(Cp2020ItemTypes.HUMANITY_LOST)) {
            int lost = rollHumanity(item, parts, dice);
            item.getProperties().put(Cp2020ItemTypes.HUMANITY_LOST, String.valueOf(lost));
            stats.getAttributes().put(Cp2020StatsStrategy.HUMANITY_LOSS, stats.getAttribute(Cp2020StatsStrategy.HUMANITY_LOSS, 0) + lost);
        }
        surgery(item).ifPresent(surgery -> operate(surgery, owner, parts, dice, health));
        markInstalled(item);
        return String.join(". ", parts);
    }

    private static int rollHumanity(Item item, List<String> parts, DiceRoller dice) {
        String cost = item.getProperties().getOrDefault(Cp2020ItemTypes.HUMANITY_COST, "0");
        if (!cost.toUpperCase().contains("D")) {
            // HOMEBREW: в таблице есть 0.5, а человечность целая: дробь вверх, иначе татуировки бесплатны
            int lost = (int) Math.ceil(Double.parseDouble(cost));
            parts.add("Человечность −" + lost);
            return lost;
        }
        List<String> steps = new ArrayList<>();
        Cp2020AttackRules.RolledDamage rolled = Cp2020AttackRules.rollText(cost, steps, dice);
        if (rolled == null) {
            throw new IllegalArgumentException("Потерю «" + cost + "» не бросить: исправьте её в правке предмета");
        }
        parts.add("Человечность −" + rolled.amount() + " (" + cost + ")");
        return rolled.amount();
    }

    private static void operate(Surgery surgery, Character owner, List<String> parts, DiceRoller dice, HealthCounterService health) {
        String text = "Хирургия " + surgery.label() + ": DC " + surgery.check() + " Medical Tech"
                + (surgery.cost() > 0 ? ", " + surgery.cost() + " eb" : "");
        int amount = rollSurgery(surgery, dice);
        boolean applied = health != null && health.applyDamage(owner.getId(), DamageRequest.builder()
                .amount(amount)
                .location(Cp2020Location.TORSO.name())
                .damageType(Cp2020DamageType.BYPASS.name())
                .autoSave(false)
                .extras(java.util.Map.of(Cp2020HealthStrategy.NO_BTM, 1))
                .build()).isPresent();
        parts.add(text + ", " + amount + " урона" + (applied ? "" : ": нанесите вручную"));
    }

    private static int rollSurgery(Surgery surgery, DiceRoller dice) {
        if (!surgery.damage().contains("D")) {
            return Integer.parseInt(surgery.damage());
        }
        Cp2020AttackRules.RolledDamage rolled = Cp2020AttackRules.rollText(surgery.damage(), new ArrayList<>(), dice);
        return rolled.amount();
    }

    private static void markInstalled(Item item) {
        int last = installed(item.getInventory().getCharacter())
                .mapToInt(other -> Integer.parseInt(other.getProperties().get(Cp2020ItemTypes.WORN)))
                .max()
                .orElse(0);
        int outermostArmor = Cp2020WornArmor.worn(item.getInventory().getCharacter()).stream()
                .mapToInt(worn -> Integer.parseInt(worn.getProperties().get(Cp2020ItemTypes.WORN)))
                .max()
                .orElse(0);
        item.getProperties().put(Cp2020ItemTypes.WORN, String.valueOf(Math.max(last, outermostArmor) + 1));
    }

    private static Optional<Surgery> surgery(Item item) {
        String code = item.getProperties().get(Cp2020ItemTypes.SURGERY);
        return SURGERIES.stream().filter(surgery -> surgery.code().equals(code)).findFirst();
    }

    static Stream<Item> installed(Character character) {
        return character == null ? Stream.empty() : character.getInventories().stream()
                .flatMap(inventory -> inventory.getContent().stream())
                .filter(Cp2020Cyber::isInstalled);
    }

    private static int sum(Character character, String key) {
        return installed(character).mapToInt(item -> Cp2020WeaponActions.number(item, key)).sum();
    }

    /** Muscle Lace, Grafted Muscle: складываются (с. 76–79) */
    static int bodyBonus(Character character) {
        return sum(character, Cp2020ItemTypes.BODY_BONUS);
    }

    static int initiativeBonus(Character character) {
        return sum(character, Cp2020ItemTypes.INITIATIVE);
    }

    /** Linear Frame: сила вместо BODY в ближнем бою; 0 — рамы нет */
    static int strength(Character character) {
        return installed(character).mapToInt(item -> Cp2020WeaponActions.number(item, Cp2020ItemTypes.STRENGTH)).max().orElse(0);
    }

    static boolean hasSpeedware(Character character) {
        return installed(character).anyMatch(item -> item.getProperties().containsKey(Cp2020ItemTypes.SPEEDWARE));
    }

    /** Кибероружие: только установленное */
    static boolean isReadyWeapon(Item item) {
        return isInstalled(item) && item.getProperties().containsKey("damage");
    }

    static InstalledBlock block(Character character) {
        List<InstalledBlock.Row> rows = installed(character)
                .map(item -> new InstalledBlock.Row(item, chip(item), details(item), actions(item)))
                .toList();
        return new InstalledBlock("Киберware", humanity(character), rows, Cp2020ItemTypes.CYBER, INSTALL, CONFIRM);
    }

    private static String humanity(Character character) {
        return character.getCounters().stream()
                .filter(counter -> Cp2020StatsStrategy.TYPE.equals(counter.getType()))
                .findFirst()
                .map(stats -> {
                    int emp = stats.getAttribute(Cp2020StatsStrategy.EMP, Cp2020StatsStrategy.DEFAULT_STAT);
                    int humanity = Cp2020Rules.humanity(emp, stats.getAttribute(Cp2020StatsStrategy.HUMANITY_LOSS, 0));
                    return "Человечность " + humanity + "/" + emp * Cp2020Rules.HUMANITY_PER_EMP + " · EMP " + Cp2020Rules.effectiveEmp(humanity);
                })
                .orElse(null);
    }

    /** Название и то, что нужно за столом: SP, урон, прибавка */
    private static String chip(Item item) {
        List<String> values = new ArrayList<>();
        if (Cp2020WornArmor.sp(item) > 0) {
            values.add("SP " + Cp2020WornArmor.sp(item));
        }
        if (item.getProperties().containsKey("damage")) {
            values.add(item.getProperties().get("damage"));
        }
        if (item.getProperties().containsKey(Cp2020ItemTypes.BODY_BONUS)) {
            values.add("BODY +" + item.getProperties().get(Cp2020ItemTypes.BODY_BONUS));
        }
        if (item.getProperties().containsKey(Cp2020ItemTypes.INITIATIVE)) {
            values.add("иниц. +" + item.getProperties().get(Cp2020ItemTypes.INITIATIVE));
        }
        if (item.getProperties().containsKey(Cp2020ItemTypes.STRENGTH)) {
            values.add("сила " + item.getProperties().get(Cp2020ItemTypes.STRENGTH));
        }
        // в названии уже есть прибавка: «Kerenzikov +1 · иниц. +1» было бы повтором
        String name = item.getName().replaceAll(" \\+\\d$", "");
        return values.isEmpty() ? name : name + " · " + String.join(" · ", values);
    }

    private static List<String> details(Item item) {
        List<String> details = new ArrayList<>();
        if (item.getDescription() != null && !item.getDescription().isBlank()) {
            details.add(item.getDescription());
        }
        String lost = item.getProperties().get(Cp2020ItemTypes.HUMANITY_LOST);
        details.add("Человечность −" + (lost != null ? lost : "0") + " · хирургия " + surgery(item).map(Surgery::label).orElse("—"));
        return details;
    }
}
