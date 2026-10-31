package app.charka.system.cp2020;

import app.charka.item.ItemAction;
import app.charka.item.ItemField.Option;
import app.charka.model.Inventory;
import app.charka.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Боепитание: выстрел и очередь, смена магазина в оружии, снаряжение магазина патронами. Книга (с. 60): стандартный магазин — «# Shots», за раунд не больше ROF.
 * Оружие заряжается магазином; оружие без магазина (револьвер, лук, см. {@code directLoad}) — патронами прямо из запаса.
 * HOMEBREW: патроны разных видов не смешиваются, при снаряжении остаток другого вида возвращается в запас.
 * Попадание, урон и режимы огня — {@link Cp2020AttackRules}; здесь ручное списание патронов и заклинивание.
 */
final class Cp2020WeaponActions {

    static final String UNJAM = "UNJAM";
    static final String FIRE = "FIRE";
    static final String BURST = "BURST";
    static final String RELOAD = "RELOAD";
    static final String EJECT = "EJECT";
    static final String ADD_MAGAZINE = "ADD_MAGAZINE";
    static final String LOAD = "LOAD";
    static final String UNLOAD = "UNLOAD";
    static final String LOAD_ALL = "LOAD_ALL";

    private static final int BURST_ROUNDS = 3;
    private static final String DAMAGE = "damage";

    private Cp2020WeaponActions() {
    }

    static List<ItemAction> actions(Item item) {
        if (Cp2020ItemTypes.WEAPON.equals(item.getType())) {
            return weaponActions(item);
        }
        if (Cp2020ItemTypes.MAGAZINE.equals(item.getType())) {
            return magazineActions(item);
        }
        return List.of();
    }

    /**
     * Штрафы от большого магазина в оружии. Solo of Fortune II; значения для I-05, I-08 и C-03 — в {@link Cp2020MagazineRules}.
     */
    static List<String> notes(Item item) {
        if (!Cp2020ItemTypes.WEAPON.equals(item.getType()) || !hasMagazine(item)) {
            return List.of();
        }
        int standard = number(item, Cp2020ItemTypes.SHOTS);
        if (!Cp2020MagazineRules.isOversized(standard, capacity(item))) {
            return List.of();
        }
        List<String> penalties = new ArrayList<>();
        String reliability = item.getProperties().get("reliability");
        if (reliability != null) {
            penalties.add("надёжность " + reliability + " → " + Cp2020MagazineRules.lowerReliability(reliability));
        }
        String concealability = item.getProperties().get("concealability");
        if (concealability != null) {
            penalties.add("скрытность " + concealability + " → " + Cp2020MagazineRules.lowerConcealability(concealability));
        }
        int snapshot = Cp2020MagazineRules.snapshotModifier(item.getProperties().get("category"));
        if (snapshot != 0) {
            penalties.add("инициатива Snapshot " + (snapshot > 0 ? "+" : "−") + Math.abs(snapshot));
        }
        return List.of("Большой магазин: " + String.join(", ", penalties));
    }

    /**
     * Действия оружия с магазином; у холодного и одноразового его нет.
     */
    private static List<ItemAction> weaponActions(Item weapon) {
        if (number(weapon, Cp2020ItemTypes.SHOTS) < 1) {
            return List.of();
        }
        int loaded = number(weapon, Cp2020ItemTypes.LOADED);
        int rof = number(weapon, Cp2020ItemTypes.RATE_OF_FIRE);
        String blocked = fireBlockedReason(weapon);

        List<ItemAction> actions = new ArrayList<>();
        if (weapon.getProperties().containsKey(Cp2020ItemTypes.JAMMED)) {
            actions.add(ItemAction.simple(UNJAM, "Расклинить", null));
        }
        ItemAction fireOne = ItemAction.simple(FIRE, "Списать патрон", blocked).asSecondary();
        actions.add(rof > 1 ? fireOne.asHidden() : fireOne);
        if (rof > 1) {
            int max = Math.min(rof, loaded);
            actions.add(ItemAction.withAmount(BURST, "Списать патроны", blocked, Math.min(BURST_ROUNDS, max), max).asSecondary());
        }
        if (isLauncher(weapon)) {
            actions.add(launcherReloadAction(weapon));
            if (weapon.getProperties().containsKey(Cp2020ItemTypes.CHARGE)) {
                actions.add(ItemAction.simple(UNLOAD, "Разрядить", null).asSecondary());
            }
            return actions;
        }
        ItemAction reload = usesMagazines(weapon) ? magazineReloadAction(weapon) : directReloadAction(weapon);
        actions.add(caliber(weapon) != null ? reload.asHidden() : reload);
        if (usesMagazines(weapon) && hasMagazine(weapon)) {
            actions.add(ItemAction.simple(EJECT, "Извлечь магазин", null).asSecondary());
        }
        if (usesMagazines(weapon)) {
            actions.add(ItemAction.simple(ADD_MAGAZINE, "＋ Магазин", null).asHidden());
            actions.add(ItemAction.simple(LOAD_ALL, "Снарядить все", null).asHidden());
        }
        return actions;
    }

    /** Расход атаки: {@link Cp2020AttackRules} */
    static String fireRounds(Item weapon, int rounds) {
        return fire(weapon, rounds);
    }

    /** Расход метательного и заряда: количеством предмета, последний уходит из инвентаря */
    static String consume(Item weapon, int units) {
        int left = quantity(weapon) - units;
        takeFromStack(weapon, units);
        return weapon.getName() + ": израсходовано " + units + ", осталось " + left;
    }

    /** Почему из оружия сейчас нельзя стрелять; null — можно */
    static String fireBlockedReason(Item weapon) {
        if (weapon.getProperties().containsKey(Cp2020ItemTypes.JAMMED)) {
            return "Оружие заклинило: расклинить — 1D6 раундов";
        }
        return number(weapon, Cp2020ItemTypes.LOADED) == 0 ? emptyReason(weapon) : null;
    }

    static String emptyReason(Item weapon) {
        if (usesMagazines(weapon)) {
            return hasMagazine(weapon) ? "Магазин пуст" : "В оружии нет магазина";
        }
        return "Оружие не заряжено";
    }

    /**
     * Снаряжённый магазин или патроны россыпью в тот магазин, что уже в оружии: одним действием,
     * без отдельных «добавить магазин» и «снарядить». HOMEBREW: книга (с. 98) знает только «перезарядка — действие», как именно, не делит.
     */
    private static ItemAction magazineReloadAction(Item weapon) {
        Stream<Option> magazines = fittingMagazines(weapon)
                .filter(magazine -> number(magazine, Cp2020ItemTypes.LOADED) > 0)
                .map(magazine -> new Option(String.valueOf(magazine.getId()), magazineLabel(magazine)));
        boolean room = hasMagazine(weapon) && number(weapon, Cp2020ItemTypes.LOADED) < capacity(weapon);
        Stream<Option> loose = room
                ? stacks(weapon).map(stack -> new Option(String.valueOf(stack.getId()), "В этот магазин: " + stackLabel(stack)))
                : Stream.empty();
        List<Option> sources = Stream.concat(magazines, loose).toList();
        int standard = number(weapon, Cp2020ItemTypes.SHOTS);
        String unavailable = sources.isEmpty()
                ? "Нет снаряжённых магазинов «" + caliber(weapon) + "» на " + standard + "–" + standard * Cp2020MagazineRules.MAX_EXTENSION
                + ": добавьте кнопкой ＋ у магазинов и снарядите патронами"
                : null;
        return ItemAction.withSources(RELOAD, "Перезарядить", unavailable, sources);
    }

    static boolean isLauncher(Item weapon) {
        return weapon.getProperties().containsKey(Cp2020ItemTypes.LAUNCHER);
    }

    /** С. 108: все виды гранат бывают и для запуска. Взрывчатка и мины — нет */
    private static Stream<Item> grenades(Item launcher) {
        return carried(launcher).filter(item -> Cp2020ItemTypes.WEAPON.equals(item.getType()) && item != launcher && quantity(item) > 0
                && item.getProperties().containsKey(Cp2020ItemTypes.AREA)
                && item.getProperties().containsKey(Cp2020ItemTypes.THROWN)
                && !item.getProperties().containsKey(Cp2020ItemTypes.EXPLOSIVE));
    }

    private static ItemAction launcherReloadAction(Item launcher) {
        List<Option> sources = grenades(launcher)
                .map(grenade -> new Option(String.valueOf(grenade.getId()), grenade.getName() + " × " + grenade.getQuantity()))
                .toList();
        return ItemAction.withSources(RELOAD, "Зарядить", sources.isEmpty() ? "Нет гранат в инвентарях персонажа: добавьте их кнопкой ＋" : null, sources);
    }

    /** Граната уходит из запаса, её урон, зона и эффект — на оружие; прежний заряд возвращается в инвентарь */
    private static String loadGrenade(Item launcher, String sourceId) {
        Item grenade = grenades(launcher)
                .filter(candidate -> String.valueOf(candidate.getId()).equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Выберите гранату"));
        if (launcher.getProperties().containsKey(Cp2020ItemTypes.CHARGE)) {
            returnCharge(launcher);
        }
        launcher.getProperties().put(Cp2020ItemTypes.CHARGE, grenade.getName());
        launcher.getProperties().put(DAMAGE, grenade.getProperties().get(DAMAGE));
        launcher.getProperties().put(Cp2020ItemTypes.AREA, grenade.getProperties().get(Cp2020ItemTypes.AREA));
        if (grenade.getProperties().containsKey(Cp2020ItemTypes.EFFECT)) {
            launcher.getProperties().put(Cp2020ItemTypes.EFFECT, grenade.getProperties().get(Cp2020ItemTypes.EFFECT));
        } else {
            launcher.getProperties().remove(Cp2020ItemTypes.EFFECT);
        }
        setLoaded(launcher, 1, launcher);
        takeFromStack(grenade, 1);
        return launcher.getName() + ": заряжено — " + grenade.getName();
    }

    private static String unloadGrenade(Item launcher) {
        String name = launcher.getProperties().get(Cp2020ItemTypes.CHARGE);
        returnCharge(launcher);
        clearCharge(launcher);
        setLoaded(launcher, 0, launcher);
        return launcher.getName() + ": разряжен, " + name + " в инвентаре";
    }

    /** Граната возвращается к таким же, а если их нет — новой строкой с теми же свойствами */
    private static void returnCharge(Item launcher) {
        String name = launcher.getProperties().get(Cp2020ItemTypes.CHARGE);
        Inventory inventory = launcher.getInventory();
        Optional<Item> same = inventory.getContent().stream()
                .filter(item -> name.equals(item.getName()) && item != launcher)
                .findFirst();
        if (same.isPresent()) {
            same.get().setQuantity(quantity(same.get()) + 1);
            return;
        }
        Item grenade = new Item();
        grenade.setName(name);
        grenade.setQuantity(1);
        grenade.setCost(0);
        grenade.setType(Cp2020ItemTypes.WEAPON);
        grenade.getProperties().put("category", "HVY");
        grenade.getProperties().put(DAMAGE, launcher.getProperties().get(DAMAGE));
        grenade.getProperties().put(Cp2020ItemTypes.AREA, launcher.getProperties().get(Cp2020ItemTypes.AREA));
        grenade.getProperties().put(Cp2020ItemTypes.THROWN, "1");
        grenade.getProperties().put(Cp2020ItemTypes.PLANTED, "1");
        if (launcher.getProperties().containsKey(Cp2020ItemTypes.EFFECT)) {
            grenade.getProperties().put(Cp2020ItemTypes.EFFECT, launcher.getProperties().get(Cp2020ItemTypes.EFFECT));
        }
        grenade.setInventory(inventory);
        inventory.getContent().add(grenade);
    }

    /** Заряд ушёл: урон снова зависит от того, что зарядят */
    private static void clearCharge(Item launcher) {
        launcher.getProperties().remove(Cp2020ItemTypes.CHARGE);
        launcher.getProperties().remove(Cp2020ItemTypes.AREA);
        launcher.getProperties().remove(Cp2020ItemTypes.EFFECT);
        launcher.getProperties().put(DAMAGE, Cp2020Weapons.BY_CHARGE);
    }

    private static ItemAction directReloadAction(Item weapon) {
        if (caliber(weapon) == null) {
            return ItemAction.simple(RELOAD, "Перезарядить", "У оружия не указан патрон: заряд задаётся в правке предмета");
        }
        return loadFromStackAction(RELOAD, "Перезарядить", weapon);
    }

    /**
     * Действия магазина, который лежит в инвентаре.
     */
    private static List<ItemAction> magazineActions(Item magazine) {
        List<ItemAction> actions = new ArrayList<>();
        boolean full = number(magazine, Cp2020ItemTypes.LOADED) == capacity(magazine);
        ItemAction load = loadFromStackAction(LOAD, full ? "Заменить патроны" : "Снарядить", magazine);
        // полному магазину заменить нечем: и кнопка, и объяснение были бы только шумом
        actions.add(full && load.unavailable() != null ? load.asHidden() : load);
        if (number(magazine, Cp2020ItemTypes.LOADED) > 0) {
            actions.add(ItemAction.simple(UNLOAD, "Разрядить", null));
        }
        return actions;
    }

    private static ItemAction loadFromStackAction(String code, String label, Item holder) {
        int capacity = capacity(holder);
        int loaded = number(holder, Cp2020ItemTypes.LOADED);
        List<Option> sources = stacks(holder)
                .filter(stack -> loaded < capacity || !sameLoad(stack, holder))
                .map(stack -> new Option(String.valueOf(stack.getId()), stackLabel(stack)))
                .toList();
        String unavailable = sources.isEmpty()
                ? (loaded == capacity ? "Заряжено полностью" : "Нет патронов «" + caliber(holder) + "» в инвентарях персонажа: добавьте их кнопкой ＋")
                : null;
        return ItemAction.withSources(code, label, unavailable, sources);
    }

    /**
     * @return итог для показа игроку
     * @throws IllegalArgumentException если действие сейчас невозможно
     */
    static String perform(Item item, String action, Map<String, String> params) {
        if (actions(item).stream().noneMatch(known -> known.code().equals(action))) {
            throw new IllegalArgumentException("Неизвестное действие с предметом: " + action);
        }
        return switch (action) {
            case UNJAM -> unjam(item);
            case FIRE -> fire(item, 1);
            case BURST -> fire(item, amount(params));
            case RELOAD -> isLauncher(item) ? loadGrenade(item, params.get("source")) : usesMagazines(item) && !isStackSource(item, params.get("source"))
                    ? changeMagazine(item, params.get("source"))
                    : loadFromStack(item, params.get("source"));
            case EJECT -> eject(item);
            case ADD_MAGAZINE -> addMagazine(item);
            case LOAD -> loadFromStack(item, params.get("source"));
            case LOAD_ALL -> loadEmptyMagazines(item, params.get("source"));
            default -> isLauncher(item) ? unloadGrenade(item) : unload(item);
        };
    }

    /** Сколько раундов это заняло, бросает мастер: учёт раундов появится в трекере (C-02) */
    private static String unjam(Item weapon) {
        weapon.getProperties().remove(Cp2020ItemTypes.JAMMED);
        return weapon.getName() + ": расклинено, это занимает 1D6 раундов";
    }

    private static String fire(Item weapon, int rounds) {
        if (weapon.getProperties().containsKey(Cp2020ItemTypes.JAMMED)) {
            throw new IllegalArgumentException(fireBlockedReason(weapon));
        }
        int loaded = number(weapon, Cp2020ItemTypes.LOADED);
        int rof = Math.max(1, number(weapon, Cp2020ItemTypes.RATE_OF_FIRE));
        if (rounds < 1) {
            throw new IllegalArgumentException("Число патронов должно быть не меньше 1");
        }
        if (rounds > rof) {
            throw new IllegalArgumentException("За раунд можно выпустить не больше " + rof + " (ROF)");
        }
        if (rounds > loaded) {
            throw new IllegalArgumentException(loaded == 0 ? emptyReason(weapon) : "В магазине только " + loaded);
        }
        setLoaded(weapon, loaded - rounds, weapon);
        if (isLauncher(weapon)) {
            clearCharge(weapon);
        }
        return weapon.getName() + ": выпущено " + rounds + ", заряжено " + (loaded - rounds) + " из " + capacity(weapon);
    }

    private static boolean isStackSource(Item weapon, String sourceId) {
        return stacks(weapon).anyMatch(stack -> String.valueOf(stack.getId()).equals(sourceId));
    }

    /** Магазин из инвентаря уходит в оружие, прежний со всеми патронами — в инвентарь рядом с оружием */
    private static String changeMagazine(Item weapon, String sourceId) {
        Item magazine = fittingMagazines(weapon)
                .filter(candidate -> String.valueOf(candidate.getId()).equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Выберите магазин, подходящий к оружию"));
        int rounds = number(magazine, Cp2020ItemTypes.LOADED);
        if (rounds == 0) {
            throw new IllegalArgumentException("Этот магазин пуст: сначала снарядите его патронами");
        }
        boolean armorPiercing = isArmorPiercing(magazine);
        int capacity = number(magazine, Cp2020ItemTypes.SHOTS);
        String previous = "";
        if (hasMagazine(weapon)) {
            previous = ", прежний магазин с " + number(weapon, Cp2020ItemTypes.LOADED) + " патр. убран в инвентарь";
            putMagazineAside(weapon);
        }
        removeFromInventory(magazine);
        setInserted(weapon, capacity);
        setLoaded(weapon, rounds, magazine);
        return weapon.getName() + ": заряжено " + rounds + " из " + capacity + kindLabel(magazine) + previous;
    }

    private static String eject(Item weapon) {
        int rounds = number(weapon, Cp2020ItemTypes.LOADED);
        putMagazineAside(weapon);
        weapon.getProperties().remove(Cp2020ItemTypes.LOADED);
        weapon.getProperties().remove(Cp2020ItemTypes.ARMOR_PIERCING);
        weapon.getProperties().remove(Cp2020ItemTypes.SLUG);
        weapon.getProperties().remove(Cp2020ItemTypes.INSERTED);
        return weapon.getName() + ": магазин с " + rounds + " патр. убран в инвентарь";
    }

    /** Пустой стандартный магазин к этому оружию; деньги не списываются, как и при добавлении из справочника */
    private static String addMagazine(Item weapon) {
        Item magazine = Cp2020Magazines.standardFor(weapon.getName(), caliber(weapon), number(weapon, Cp2020ItemTypes.SHOTS),
                weapon.getProperties().get("category"));
        magazine.setInventory(weapon.getInventory());
        weapon.getInventory().getContent().add(magazine);
        return weapon.getName() + ": в инвентарь добавлен пустой магазин на " + number(weapon, Cp2020ItemTypes.SHOTS);
    }

    private static void putMagazineAside(Item weapon) {
        Item magazine = Cp2020Magazines.newMagazine(caliber(weapon), capacity(weapon),
                number(weapon, Cp2020ItemTypes.LOADED), isArmorPiercing(weapon), isSlug(weapon));
        magazine.setInventory(weapon.getInventory());
        weapon.getInventory().getContent().add(magazine);
    }

    /** Размер хранится, только если магазин не стандартный: строка обычного оружия остаётся как в книге */
    private static void setInserted(Item weapon, int capacity) {
        if (capacity == number(weapon, Cp2020ItemTypes.SHOTS)) {
            weapon.getProperties().remove(Cp2020ItemTypes.INSERTED);
        } else {
            weapon.getProperties().put(Cp2020ItemTypes.INSERTED, String.valueOf(capacity));
        }
    }

    /** Патроны из запаса в магазин или в оружие без магазина, сколько поместится */
    private static String loadFromStack(Item holder, String sourceId) {
        Item stack = stacks(holder)
                .filter(candidate -> String.valueOf(candidate.getId()).equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Выберите запас патронов того же калибра"));
        int capacity = capacity(holder);
        int loaded = number(holder, Cp2020ItemTypes.LOADED);

        int returned = 0;
        if (loaded > 0 && !sameLoad(holder, stack)) {
            returned = loaded;
            returnToStock(holder, returned);
            loaded = 0;
        }
        int taken = Math.min(capacity - loaded, stack.getQuantity());
        if (taken == 0) {
            throw new IllegalArgumentException("Заряжено полностью");
        }
        takeFromStack(stack, taken);
        setLoaded(holder, loaded + taken, stack);

        return holder.getName() + ": заряжено " + (loaded + taken) + " из " + capacity + kindLabel(stack)
                + ", из запаса взято " + taken
                + (returned > 0 ? ", в запас возвращено " + returned : "");
    }

    /** Все пустые магазины к оружию из одного запаса, пока он не кончится */
    private static String loadEmptyMagazines(Item weapon, String sourceId) {
        Item stack = stacks(weapon)
                .filter(candidate -> String.valueOf(candidate.getId()).equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Выберите запас патронов того же калибра"));
        int before = stack.getQuantity();
        int magazines = 0;
        for (Item magazine : fittingMagazines(weapon).filter(candidate -> number(candidate, Cp2020ItemTypes.LOADED) == 0).toList()) {
            if (stack.getQuantity() == 0) {
                break;
            }
            loadFromStack(magazine, sourceId);
            magazines++;
        }
        if (magazines == 0) {
            throw new IllegalArgumentException("Пустых магазинов нет");
        }
        return weapon.getName() + ": снаряжено магазинов " + magazines + ", из запаса взято " + (before - stack.getQuantity());
    }

    private static String unload(Item magazine) {
        int rounds = number(magazine, Cp2020ItemTypes.LOADED);
        returnToStock(magazine, rounds);
        setLoaded(magazine, 0, magazine);
        return magazine.getName() + ": разряжен, в запас возвращено " + rounds;
    }

    /** Патроны возвращаются в такой же запас рядом с магазином или оружием, а если его нет — в новый */
    private static void returnToStock(Item holder, int rounds) {
        boolean armorPiercing = isArmorPiercing(holder);
        Inventory inventory = holder.getInventory();
        Optional<Item> same = inventory.getContent().stream()
                .filter(item -> isStackFor(item, caliber(holder)) && sameLoad(item, holder))
                .findFirst();
        if (same.isPresent()) {
            same.get().setQuantity(quantity(same.get()) + rounds);
            return;
        }
        Item stack = new Item();
        stack.setName(isSlug(holder) ? Cp2020Ammo.slugStackName(caliber(holder)) : Cp2020Ammo.stackName(caliber(holder), armorPiercing));
        stack.setQuantity(rounds);
        stack.setCost(0);
        stack.setType(Cp2020ItemTypes.AMMO);
        stack.getProperties().put(Cp2020ItemTypes.CALIBER, caliber(holder));
        if (armorPiercing) {
            stack.getProperties().put(Cp2020ItemTypes.ARMOR_PIERCING, "1");
        }
        if (isSlug(holder)) {
            stack.getProperties().put(Cp2020ItemTypes.SLUG, "1");
        }
        stack.setInventory(inventory);
        inventory.getContent().add(stack);
    }

    /** Пустая коробка исчезает: в форме предмета количество не меньше 1 */
    private static void takeFromStack(Item stack, int rounds) {
        int left = stack.getQuantity() - rounds;
        stack.setQuantity(left);
        if (left == 0) {
            removeFromInventory(stack);
        }
    }

    private static void removeFromInventory(Item item) {
        // по ссылке: equals сущностей Lombok ходит по связям с инвентарём
        item.getInventory().getContent().removeIf(candidate -> candidate == item);
    }

    /** @param source откуда заряд: от него вид патронов; пустой магазин вида не имеет */
    private static void setLoaded(Item holder, int loaded, Item source) {
        boolean armorPiercing = isArmorPiercing(source);
        boolean slug = isSlug(source);
        holder.getProperties().put(Cp2020ItemTypes.LOADED, String.valueOf(loaded));
        if (armorPiercing && loaded > 0) {
            holder.getProperties().put(Cp2020ItemTypes.ARMOR_PIERCING, "1");
        } else {
            holder.getProperties().remove(Cp2020ItemTypes.ARMOR_PIERCING);
        }
        if (slug && loaded > 0) {
            holder.getProperties().put(Cp2020ItemTypes.SLUG, "1");
        } else {
            holder.getProperties().remove(Cp2020ItemTypes.SLUG);
        }
    }

    /** Один вид патронов: разные в одном магазине не смешиваются */
    private static boolean sameLoad(Item a, Item b) {
        return isArmorPiercing(a) == isArmorPiercing(b) && isSlug(a) == isSlug(b);
    }

    private static String kindLabel(Item item) {
        return isSlug(item) ? " пули" : isArmorPiercing(item) ? " AP" : "";
    }

    /** Запасы патронов этого калибра во всех инвентарях владельца; сначала те, что лежат рядом */
    static Stream<Item> stacks(Item holder) {
        String caliber = caliber(holder);
        if (caliber == null) {
            return Stream.empty();
        }
        return carried(holder).filter(item -> isStackFor(item, caliber) && quantity(item) > 0);
    }

    /**
     * Магазины, которые встают в оружие: тот же патрон и подходящий размер ({@link Cp2020MagazineRules#fits}).
     * Книга: Bushmaster (5.56, 30) «uses the M-16A1 clip» (5.56, 30) — магазин подходит по патрону и размеру, а не по модели. Самые полные — первыми.
     */
    static Stream<Item> fittingMagazines(Item weapon) {
        int standard = number(weapon, Cp2020ItemTypes.SHOTS);
        return carried(weapon)
                .filter(item -> Cp2020ItemTypes.MAGAZINE.equals(item.getType())
                        && caliber(weapon).equalsIgnoreCase(caliber(item))
                        && Cp2020MagazineRules.fits(standard, number(item, Cp2020ItemTypes.SHOTS)))
                .sorted((a, b) -> number(b, Cp2020ItemTypes.LOADED) - number(a, Cp2020ItemTypes.LOADED));
    }

    private static Stream<Item> carried(Item item) {
        Inventory own = item.getInventory();
        Stream<Inventory> others = own.getCharacter().getInventories().stream().filter(inventory -> inventory != own);
        return Stream.concat(Stream.of(own), others).flatMap(inventory -> inventory.getContent().stream());
    }

    private static boolean isStackFor(Item item, String caliber) {
        return Cp2020ItemTypes.AMMO.equals(item.getType()) && caliber.equalsIgnoreCase(caliber(item));
    }

    static boolean usesMagazines(Item weapon) {
        return Cp2020ItemTypes.WEAPON.equals(weapon.getType()) && caliber(weapon) != null
                && !weapon.getProperties().containsKey(Cp2020ItemTypes.DIRECT_LOAD);
    }

    /** Заряд есть в свойствах, пока магазин в оружии, даже пустой */
    static boolean hasMagazine(Item weapon) {
        return weapon.getProperties().containsKey(Cp2020ItemTypes.LOADED);
    }

    /** Сколько патронов сейчас вмещается: размер вставленного магазина, иначе стандартный */
    static int capacity(Item holder) {
        return holder.getProperties().containsKey(Cp2020ItemTypes.INSERTED)
                ? number(holder, Cp2020ItemTypes.INSERTED)
                : number(holder, Cp2020ItemTypes.SHOTS);
    }

    static String stackLabel(Item stack) {
        return caliber(stack) + kindLabel(stack) + " × " + stack.getQuantity() + " (" + stack.getInventory().getName() + ")";
    }

    private static String magazineLabel(Item magazine) {
        return number(magazine, Cp2020ItemTypes.LOADED) + " из " + number(magazine, Cp2020ItemTypes.SHOTS)
                + kindLabel(magazine) + " (" + magazine.getInventory().getName() + ")";
    }

    private static int amount(Map<String, String> params) {
        try {
            return Integer.parseInt(params.getOrDefault("amount", "").strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Укажите, сколько патронов выпустить");
        }
    }

    static String caliber(Item item) {
        String caliber = item.getProperties().get(Cp2020ItemTypes.CALIBER);
        return caliber == null || caliber.isBlank() ? null : caliber.strip();
    }

    static boolean isSlug(Item item) {
        return item.getProperties().containsKey(Cp2020ItemTypes.SLUG);
    }

    static boolean isArmorPiercing(Item item) {
        return item.getProperties().containsKey(Cp2020ItemTypes.ARMOR_PIERCING);
    }

    static int quantity(Item item) {
        return item.getQuantity() == null ? 0 : item.getQuantity();
    }

    static int number(Item item, String key) {
        String value = item.getProperties().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
