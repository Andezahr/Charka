package app.charka.system.kosmozeki;

import app.charka.item.ItemAction;
import app.charka.item.ItemAttachments;
import app.charka.item.ItemChip;
import app.charka.item.ItemField.Option;
import app.charka.item.ItemSummary;
import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Действия с предметами Космозэков (K-04, K-08): перезарядка из запаса (действие хода), надеть и снять броню,
 * «Жидкая кожа» и другие лечилки.
 */
final class KzItemActions {

    static final String RELOAD = "RELOAD";
    static final String WEAR = app.charka.item.ItemAction.WEAR;
    static final String TAKE_OFF = "TAKE_OFF";
    static final String HEAL_ONE = "HEAL_ONE";
    static final String HEAL_ALL = "HEAL_ALL";

    private KzItemActions() {
    }

    static List<ItemAction> actions(Item item) {
        if (KzItems.isRanged(item)) {
            int capacity = KzItems.number(item, KzItems.CAPACITY, 0);
            int loaded = KzItems.loaded(item);
            List<Item> stacks = KzItems.ammoFor(item);
            String unavailable = capacity == 0 ? "У оружия не указан магазин"
                    : loaded >= capacity ? "Магазин полон"
                    : stacks.isEmpty() ? "Нет патронов" + caliber(item) : null;
            List<Option> sources = stacks.stream()
                    .map(stack -> new Option(String.valueOf(stack.getId()), stack.getName() + " · " + stack.getQuantity()))
                    .toList();
            return List.of(new ItemAction(RELOAD, "Перезарядить", unavailable, sources.size() > 1 ? sources : List.of(),
                    null, null, false, false, true, null).withLabels("Чем", null));
        }
        if (KzItems.isArmor(item)) {
            return List.of(KzItems.isWorn(item)
                    ? ItemAction.simple(TAKE_OFF, "Снять", null)
                    : ItemAction.simple(WEAR, "Надеть", null));
        }
        if (KzItems.isMedicine(item)) {
            Character owner = owner(item);
            Counter body = KzAbilities.bodyCounter(owner).orElse(null);
            List<ItemAction> actions = new ArrayList<>();
            int one = KzItems.number(item, KzItems.HEAL_ONE, 0);
            int all = KzItems.number(item, KzItems.HEAL_ALL, 0);
            String noBody = body == null ? "У персонажа нет счётчика тела" : null;
            if (one > 0) {
                List<Option> zones = body == null ? List.of() : KzBodyStrategy.body(body).zones().stream()
                        .sorted((a, b) -> Integer.compare(KzBodyStrategy.hp(body, a) * 100 / a.maxHp(), KzBodyStrategy.hp(body, b) * 100 / b.maxHp()))
                        .map(zone -> new Option(zone.code(), zone.name() + " " + KzBodyStrategy.hp(body, zone) + "/" + zone.maxHp()))
                        .toList();
                actions.add(new ItemAction(HEAL_ONE, "+" + one + " в зону", noBody, zones, null, null, false, false, true, null)
                        .withLabels("Куда", null));
            }
            if (all > 0) {
                actions.add(new ItemAction(HEAL_ALL, "+" + all + " во все зоны", noBody, List.of(), null, null, false, false, true, null));
            }
            return actions;
        }
        return List.of();
    }

    private static String caliber(Item item) {
        String caliber = item.getProperties().get(KzItems.CALIBER);
        return caliber == null || caliber.isBlank() ? "" : ": " + caliber.toLowerCase();
    }

    private static Character owner(Item item) {
        return item.getInventory() == null ? null : item.getInventory().getCharacter();
    }

    static String perform(Item item, String action, Map<String, String> params) {
        return switch (action) {
            case RELOAD -> reload(item, params.get("source"));
            case WEAR -> wear(item);
            case TAKE_OFF -> {
                item.getProperties().remove(KzItems.WORN);
                yield "Снято: " + item.getName();
            }
            case HEAL_ONE -> heal(item, params.get("source"), true);
            case HEAL_ALL -> heal(item, null, false);
            default -> throw new IllegalArgumentException("Неизвестное действие с предметом: " + action);
        };
    }

    /** Магазин дополняется из запаса до полного; выбран запас — из него, иначе из первого подходящего */
    private static String reload(Item weapon, String source) {
        int capacity = KzItems.number(weapon, KzItems.CAPACITY, 0);
        int loaded = KzItems.loaded(weapon);
        if (capacity == 0) {
            throw new IllegalArgumentException("У оружия не указан магазин");
        }
        if (loaded >= capacity) {
            throw new IllegalArgumentException("Магазин полон");
        }
        List<Item> stacks = KzItems.ammoFor(weapon);
        Item stack = stacks.stream()
                .filter(candidate -> source == null || source.isBlank() || String.valueOf(candidate.getId()).equals(source))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет патронов" + caliber(weapon)));
        int moved = Math.min(capacity - loaded, stack.getQuantity());
        weapon.getProperties().put(KzItems.LOADED, String.valueOf(loaded + moved));
        int left = stack.getQuantity() - moved;
        if (left <= 0) {
            stack.getInventory().getContent().remove(stack);
        } else {
            stack.setQuantity(left);
        }
        return "Перезаряжено: " + (loaded + moved) + "/" + capacity + ", в запасе " + Math.max(0, left);
    }

    /** На зоне одна броня: надетая на те же зоны снимается */
    private static String wear(Item armor) {
        Set<String> zones = zones(armor);
        List<String> removed = new ArrayList<>();
        KzItems.wornArmor(owner(armor))
                .filter(other -> other != armor && zones(other).stream().anyMatch(zones::contains))
                .toList()
                .forEach(other -> {
                    other.getProperties().remove(KzItems.WORN);
                    removed.add(other.getName());
                });
        armor.getProperties().put(KzItems.WORN, "1");
        return "Надето: " + armor.getName() + (removed.isEmpty() ? "" : "; снято: " + String.join(", ", removed));
    }

    /** Какие зоны человека закрывает броня */
    private static Set<String> zones(Item armor) {
        return KzBody.HUMAN.zones().stream().map(KzBody.Zone::code)
                .filter(code -> KzItems.covers(armor, code))
                .collect(Collectors.toSet());
    }

    private static String heal(Item medicine, String zone, boolean one) {
        Counter body = KzAbilities.bodyCounter(owner(medicine))
                .orElseThrow(() -> new IllegalArgumentException("У персонажа нет счётчика тела"));
        String result;
        if (one) {
            String code = zone;
            if (code == null || code.isBlank()) {
                // одна кнопка: в самую раненую зону
                code = KzBodyStrategy.body(body).zones().stream()
                        .min((a, b) -> Integer.compare(KzBodyStrategy.hp(body, a) * 100 / a.maxHp(), KzBodyStrategy.hp(body, b) * 100 / b.maxHp()))
                        .map(KzBody.Zone::code).orElseThrow();
            }
            result = KzBodyStrategy.healZone(body, code, KzItems.number(medicine, KzItems.HEAL_ONE, 0)).getSummary();
        } else {
            int points = KzItems.number(medicine, KzItems.HEAL_ALL, 0);
            int healed = KzBodyStrategy.healAll(body, points);
            result = "+" + points + " во все зоны, вылечено " + healed;
        }
        int quantity = medicine.getQuantity() == null ? 1 : medicine.getQuantity();
        if (quantity <= 1) {
            medicine.getInventory().getContent().remove(medicine);
        } else {
            medicine.setQuantity(quantity - 1);
        }
        return medicine.getName() + ": " + result;
    }

    /** Краткая строка: «20 урона · ROF 2 · DIF 30 · 5/7» */
    static ItemSummary summary(Item item) {
        List<String> lines = new ArrayList<>();
        if (KzItems.isRanged(item)) {
            lines.add(KzItems.number(item, KzItems.DMG, 0) + " урона");
            lines.add("ROF " + KzItems.number(item, KzItems.ROF, 1));
            lines.add("DIF " + KzItems.number(item, KzItems.DIF, 0));
            return new ItemSummary(KzItems.loaded(item) + "/" + KzItems.number(item, KzItems.CAPACITY, 0), lines);
        }
        if (KzItems.isMelee(item)) {
            lines.add(KzItems.number(item, KzItems.DMG, 0) + " урона");
            int attacks = KzItems.number(item, KzItems.ATTACKS, 1);
            if (attacks > 1) {
                lines.add("×" + attacks + " за ход");
            }
            lines.add("DIF " + KzItems.number(item, KzItems.DIF, 0));
            if (item.getProperties().containsKey(KzItems.ARMOR_HALF)) {
                lines.add("броня ½");
            }
            return new ItemSummary(null, lines);
        }
        if (KzItems.isArmor(item)) {
            String zone = KzItems.ARMOR_ZONES.stream().filter(option -> option.value().equals(item.getProperties().get(KzItems.ZONE)))
                    .map(Option::label).findFirst().orElse("?");
            return new ItemSummary(KzItems.isWorn(item) ? "надета" : null,
                    List.of(zone, "броня " + KzItems.number(item, KzItems.ARMOR_VALUE, 0)));
        }
        if (KzItems.MEDICINE.equals(item.getType())) {
            // UX-28: «+50 в зону» и «+30 во все» уже написаны на кнопках — второй раз текстом не повторяем
            return new ItemSummary(null, List.of());
        }
        return null;
    }

    /** Под оружием — фишка запаса патронов */
    static ItemAttachments attachments(Item item) {
        if (!KzItems.isRanged(item)) {
            return ItemAttachments.NONE;
        }
        int spare = KzItems.ammoFor(item).stream().mapToInt(Item::getQuantity).sum();
        ItemChip chip = ItemChip.info("Запас " + spare, spare == 0 ? "Подходящих патронов нет" + caliber(item) : "Патронов в инвентарях");
        return new ItemAttachments(List.of(new ItemAttachments.Group("Патроны", List.of(chip))), List.of(), "", null);
    }
}
