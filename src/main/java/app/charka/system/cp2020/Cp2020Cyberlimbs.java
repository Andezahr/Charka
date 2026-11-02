package app.charka.system.cp2020;

import app.charka.model.Character;
import app.charka.model.Item;
import app.charka.model.counters.Counter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Киберконечности (с. 89–90): сторона, опции в конечности, прочность как у машины, удар и пинок.
 * Конечность при установке занимает первую свободную сторону (правую, потом левую); сторону можно поменять в правке предмета.
 * Опция крепится к последней установленной подходящей конечности, где есть место.
 */
final class Cp2020Cyberlimbs {

    static final String ARM = "Cyberarm";
    static final String LEG = "Cyberleg";

    /** С. 89: 20 урона — не работает, 30 — уничтожена; гидравлика 30 и 40 */
    static final int DISABLED = 20;
    static final int DESTROYED = 30;
    private static final int HYDRAULIC_EXTRA = 10;

    /** Лимит опций: рука 4 (с. 89), нога 3 (таблица с. 79) */
    private static final int ARM_OPTIONS = 4;
    private static final int LEG_OPTIONS = 3;

    /** С. 89: удар киберрукой 1D6, пинок киберногой 2D6 */
    private static final String ARM_BLOW = "1D6";
    private static final String LEG_BLOW = "2D6";

    /** Limb Armor (с. 90): SP 20 */
    static final int ARMOR_SP = 20;

    private static final List<Cp2020Location> ARM_SIDES = List.of(Cp2020Location.R_ARM, Cp2020Location.L_ARM);
    private static final List<Cp2020Location> LEG_SIDES = List.of(Cp2020Location.R_LEG, Cp2020Location.L_LEG);

    private Cp2020Cyberlimbs() {
    }

    static boolean isLimb(Item item) {
        return ARM.equals(item.getName()) || LEG.equals(item.getName());
    }

    /** Опция, которая живёт в конечности: всё, что требует руку или ногу */
    static boolean isOption(Item item) {
        String requires = item.getProperties().get(Cp2020ItemTypes.REQUIRES);
        return requires != null && Stream.of(requires.split("\\|")).anyMatch(name -> name.equals(ARM) || name.equals(LEG));
    }

    static Optional<Cp2020Location> zone(Item limb) {
        String zone = limb.getProperties().get(Cp2020ItemTypes.LIMB_ZONE);
        if (zone == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Cp2020Location.parse(zone));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static List<Cp2020Location> sides(Item limb) {
        return ARM.equals(limb.getName()) ? ARM_SIDES : LEG_SIDES;
    }

    static Stream<Item> limbs(Character character) {
        return Cp2020Cyber.installed(character).filter(Cp2020Cyberlimbs::isLimb);
    }

    /** Установленная конечность в этой зоне */
    static Optional<Item> at(Character character, Cp2020Location location) {
        return limbs(character).filter(limb -> zone(limb).filter(location::equals).isPresent()).findFirst();
    }

    /** Установленные опции конечности */
    static Stream<Item> options(Item limb) {
        String id = String.valueOf(limb.getId());
        return Cp2020Cyber.installed(limb.getInventory().getCharacter())
                .filter(option -> id.equals(option.getProperties().get(Cp2020ItemTypes.LIMB_HOST)));
    }

    private static int capacity(Item limb) {
        return ARM.equals(limb.getName()) ? ARM_OPTIONS : LEG_OPTIONS;
    }

    /** Покрытие (пластик, хром, RealSkinn, броня) места опции не занимает */
    private static boolean takesSpace(Item option) {
        return !option.getProperties().containsKey(Cp2020ItemTypes.LIMB_COVERING);
    }

    private static boolean fits(Item limb, Item option) {
        List<Item> present = options(limb).toList();
        if (!takesSpace(option)) {
            // с. 90: бронированную конечность не покрывают и не хромируют, и одно покрытие поверх другого не ложится
            return present.stream().noneMatch(other -> other.getProperties().containsKey(Cp2020ItemTypes.LIMB_COVERING));
        }
        boolean end = option.getProperties().containsKey(Cp2020ItemTypes.LIMB_END);
        if (end && present.stream().anyMatch(other -> other.getProperties().containsKey(Cp2020ItemTypes.LIMB_END))) {
            return false;
        }
        return present.stream().filter(Cp2020Cyberlimbs::takesSpace).count() < capacity(limb);
    }

    /** Почему нельзя установить; null — можно */
    static String blockedReason(Item item) {
        Character owner = item.getInventory().getCharacter();
        if (isLimb(item)) {
            return freeSide(item).isPresent() ? null
                    : (ARM.equals(item.getName()) ? "Обе руки уже кибер" : "Обе ноги уже кибер");
        }
        if (isOption(item) && host(owner, item).isEmpty()) {
            if (!item.getProperties().containsKey(Cp2020ItemTypes.LIMB_COVERING)) {
                return "Нет места: в руке " + ARM_OPTIONS + " опции, в ноге " + LEG_OPTIONS + ", кисть или стопа одна (с. 89)";
            }
            return "Конечность уже покрыта: снимите прежнее покрытие";
        }
        return null;
    }

    private static Optional<Cp2020Location> freeSide(Item limb) {
        Character owner = limb.getInventory().getCharacter();
        return sides(limb).stream().filter(side -> at(owner, side).isEmpty()).findFirst();
    }

    private static Optional<Item> host(Character owner, Item option) {
        List<String> parents = List.of(option.getProperties().get(Cp2020ItemTypes.REQUIRES).split("\\|"));
        return limbs(owner)
                .filter(limb -> parents.contains(limb.getName()))
                .filter(limb -> fits(limb, option))
                // последняя поставленная: за столом ставят конечность, потом её опции
                .max(Comparator.comparingInt(limb -> Cp2020WeaponActions.number(limb, Cp2020ItemTypes.WORN)));
    }

    /**
     * Перед отметкой «установлено»: конечности — сторона, опции — конечность. Конечность на месте потерянной снимает отметку потери.
     *
     * @return что сказать игроку; null — нечего
     */
    static String attach(Item item) {
        Character owner = item.getInventory().getCharacter();
        if (isLimb(item)) {
            Cp2020Location side = freeSide(item).orElseThrow();
            item.getProperties().put(Cp2020ItemTypes.LIMB_ZONE, side.name());
            item.getProperties().remove(Cp2020ItemTypes.LIMB_DAMAGE);
            health(owner).ifPresent(health -> health.setFlag(Cp2020HealthStrategy.LOST_PREFIX + side.name(), false));
            return side.getLabel();
        }
        if (!isOption(item)) {
            return null;
        }
        Item limb = host(owner, item).orElseThrow();
        item.getProperties().put(Cp2020ItemTypes.LIMB_HOST, String.valueOf(limb.getId()));
        Cp2020Location side = zone(limb).orElse(null);
        if (side != null && item.getProperties().containsKey(Cp2020ItemTypes.ARMOR_SP)) {
            // Limb Armor защищает зону своей конечности: слоем брони, как пластины
            item.getProperties().put(Cp2020ItemTypes.zoneKey(side), "1");
        }
        return side == null ? limb.getName() : side.getLabel();
    }

    /**
     * Снятие: опция отцепляется от конечности, конечность снимается вместе с опциями.
     *
     * @return снятые вместе с конечностью опции
     */
    static List<String> detach(Item item) {
        List<String> removed = List.of();
        if (isLimb(item)) {
            removed = options(item).map(option -> {
                detachOption(option);
                option.getProperties().remove(Cp2020ItemTypes.WORN);
                return option.getName();
            }).toList();
            item.getProperties().remove(Cp2020ItemTypes.LIMB_ZONE);
        }
        detachOption(item);
        return removed;
    }

    private static void detachOption(Item option) {
        option.getProperties().remove(Cp2020ItemTypes.LIMB_HOST);
        if (option.getProperties().containsKey(Cp2020ItemTypes.LIMB_COVERING)) {
            for (Cp2020Location location : Cp2020Location.values()) {
                option.getProperties().remove(Cp2020ItemTypes.zoneKey(location));
            }
        }
    }

    static int damage(Item limb) {
        return Cp2020WeaponActions.number(limb, Cp2020ItemTypes.LIMB_DAMAGE);
    }

    /** Сколько выдержит, пока работает: 20, гидравлика 30, Reinforced и Thickened +5 каждая */
    static int disabledAt(Item limb) {
        int base = options(limb).anyMatch(option -> option.getProperties().containsKey(Cp2020ItemTypes.LIMB_HYDRAULIC))
                ? DISABLED + HYDRAULIC_EXTRA : DISABLED;
        return base + options(limb).mapToInt(option -> Cp2020WeaponActions.number(option, Cp2020ItemTypes.LIMB_SDP)).sum();
    }

    static int destroyedAt(Item limb) {
        return disabledAt(limb) + (DESTROYED - DISABLED);
    }

    static boolean works(Item limb) {
        return damage(limb) < disabledAt(limb);
    }

    static boolean isDestroyed(Item limb) {
        return damage(limb) >= destroyedAt(limb);
    }

    static boolean isRepairable(Item limb) {
        return isLimb(limb) && damage(limb) > 0 && !isDestroyed(limb);
    }

    static void repair(Item limb) {
        limb.getProperties().remove(Cp2020ItemTypes.LIMB_DAMAGE);
    }

    /**
     * Попадание в киберконечность после брони: урон идёт в её прочность, как у машины (с. 89).
     * Боли нет, спасброска от оглушения нет (с. 89), BTM не вычитается: это свойство тела, а не металла.
     * HOMEBREW: урон сверх прочности и попадания в уничтоженную конечность на тело не переходят, книга об этом молчит.
     *
     * @return итог для показа
     */
    static String hit(Item limb, int amount, Consumer<String> steps) {
        int before = damage(limb);
        int after = Math.min(before + amount, destroyedAt(limb));
        limb.getProperties().put(Cp2020ItemTypes.LIMB_DAMAGE, String.valueOf(after));
        String where = zone(limb).map(Cp2020Location::getLabel).orElse(limb.getName());
        steps.accept("Киберконечность: урон в прочность, без BTM и спасброска — " + before + " → " + after
                + " (не работает с " + disabledAt(limb) + ", уничтожена с " + destroyedAt(limb) + ")");
        return limb.getName() + " (" + where + ") получает " + amount + " урона" + state(limb).map(state -> ": " + state).orElse("");
    }

    static Optional<String> state(Item limb) {
        if (isDestroyed(limb)) {
            return Optional.of("уничтожена");
        }
        return works(limb) ? Optional.empty() : Optional.of("не работает");
    }

    /**
     * Удар киберрукой или пинок киберногой: кубики конечности, умноженные гидравликой (×3) или утолщённым миомером (×2).
     *
     * @param kick пинок; иначе удар рукой
     */
    record Blow(String label, String dice, int multiplier) {

        String text() {
            return dice + (multiplier > 1 ? "×" + multiplier : "");
        }
    }

    static Optional<Blow> blow(Character character, boolean kick) {
        String name = kick ? LEG : ARM;
        return limbs(character)
                .filter(limb -> name.equals(limb.getName()) && works(limb))
                .map(limb -> new Blow(kick ? "Киберпинок" : "Киберудар", kick ? LEG_BLOW : ARM_BLOW, multiplier(limb)))
                .max(Comparator.comparingInt(Blow::multiplier));
    }

    private static int multiplier(Item limb) {
        return options(limb).mapToInt(option -> Cp2020WeaponActions.number(option, Cp2020ItemTypes.LIMB_POWER)).max().orElse(1);
    }

    /** Короткая подпись для фишки: сторона и состояние */
    static String chipSuffix(Item item) {
        if (isLimb(item)) {
            StringBuilder text = new StringBuilder(zone(item).map(Cp2020Location::getShortLabel).orElse(""));
            if (damage(item) > 0) {
                text.append(text.isEmpty() ? "" : " · ").append(damage(item)).append("/").append(disabledAt(item));
            }
            state(item).ifPresent(state -> text.append(" · ").append(state));
            return text.toString();
        }
        String hostId = item.getProperties().get(Cp2020ItemTypes.LIMB_HOST);
        if (hostId == null) {
            return "";
        }
        return limbs(item.getInventory().getCharacter())
                .filter(limb -> hostId.equals(String.valueOf(limb.getId())))
                .findFirst()
                .flatMap(Cp2020Cyberlimbs::zone)
                .map(Cp2020Location::getShortLabel)
                .orElse("");
    }

    /** Строка о прочности для меню фишки */
    static Optional<String> details(Item item) {
        if (!isLimb(item)) {
            return Optional.empty();
        }
        long used = options(item).filter(Cp2020Cyberlimbs::takesSpace).count();
        return Optional.of("Прочность: урон " + damage(item) + ", не работает с " + disabledAt(item) + ", уничтожена с " + destroyedAt(item)
                + " · опций " + used + " из " + capacity(item));
    }

    private static Optional<Counter> health(Character character) {
        return character.getCounters().stream()
                .filter(counter -> Cp2020HealthStrategy.TYPE.equals(counter.getType()))
                .findFirst();
    }
}
