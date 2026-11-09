package app.charka.system.kosmozeki;

import app.charka.item.ItemField.Option;
import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.AbstractCounterStrategy;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.model.counters.strategy.health.HealthCounterStrategy;
import app.charka.model.counters.strategy.health.HealthEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Тело Космозэков (K-02, K-03): у каждой зоны своё ХП, общего нет.
 * <p>
 * Набор зон — текст {@link #ZONES} ({@link KzBody}), у персонажей игроков — «Человек». Значение счётчика — сколько ХП
 * потеряно всего (для журнала здоровья). attributes: {@code lost_<зона>} — потеряно в зоне, {@code armor_<зона>} — броня,
 * заданная вручную (у зверей и роботов; у людей броня — надетые предметы), флаги {@link #AWAKE}, {@link #DEAD},
 * {@link #REST} — как персонаж отдыхает при промотке времени.
 * <p>
 * Урон одного попадания: урон − броня зоны (против оружия «броня ½» — половина брони, вниз) → × множитель зоны, не меньше 0.
 * Зона на нуле урона не получает. Любая зона на нуле — без сознания, пока мастер не отметит иначе.
 */
@Component
@RequiredArgsConstructor
public class KzBodyStrategy extends AbstractCounterStrategy implements HealthCounterStrategy {

    public static final String TYPE = "KZ_BODY";
    /** Текст набора зон */
    public static final String ZONES = "zones";
    public static final String LOST_PREFIX = "lost_";
    public static final String ARMOR_PREFIX = "armor_";
    /** Ввод формы: текущее ХП зоны */
    public static final String HP_PREFIX = "hp_";
    /** Мастер поднял: в сознании, хотя зона на нуле */
    public static final String AWAKE = "awake";
    public static final String DEAD = "dead";
    /** Отдых при промотке времени: 0 — нет, 1 — сон, 2 — сон и еда */
    public static final String REST = "rest";

    /** {@code DamageRequest.extras}: сколько попаданий одним уроном */
    public static final String HITS = "hits";
    /** {@code DamageRequest.extras}: больше 0 — броня вдвое меньше (оружие ближнего боя) */
    public static final String ARMOR_HALF = "armorHalf";
    /** {@code DamageRequest.extras}: урон сверх попаданий (крит 100) — после брони и множителя */
    public static final String BONUS = "bonus";
    /** {@code DamageRequest.extras}: последняя цифра броска, по ней — зона; нет — бросок к10 */
    public static final String DIGIT = "digit";

    static final int REST_NONE = 0;
    static final int REST_SLEEP = 1;
    static final int REST_FED = 2;
    /** За день: сон — 30 ХП в каждую зону, сон и еда — 60 */
    static final int HEAL_SLEEP = 30;
    static final int HEAL_FED = 60;
    /** HOMEBREW: по умолчанию персонаж спит и ест — так обычно и бывает между сценами */
    static final int DEFAULT_REST = REST_FED;

    static final List<Option> REST_OPTIONS = List.of(
            new Option(String.valueOf(REST_FED), "Сон и еда: +" + HEAL_FED + " в день"),
            new Option(String.valueOf(REST_SLEEP), "Только сон: +" + HEAL_SLEEP + " в день"),
            new Option(String.valueOf(REST_NONE), "Без отдыха"));

    private final DiceRoller dice;

    // ---------- CounterStrategy ----------

    @Override
    public String getCounterType() {
        return TYPE;
    }

    @Override
    public Set<String> getSystems() {
        return Set.of(KzSystem.CODE);
    }

    @Override
    public String getDisplayName() {
        return "Тело (Космозэки)";
    }

    @Override
    public String getDefaultName() {
        return "Здоровье";
    }

    @Override
    public String getViewTemplate() {
        return "counters/types/kz-body";
    }

    @Override
    public String getCompactTemplate() {
        return "counters/types/kz-body";
    }

    @Override
    public boolean usesInitialValue() {
        return false;
    }

    @Override
    public boolean isUniquePerCharacter() {
        return true;
    }

    @Override
    public int getDisplayOrder() {
        return 10;
    }

    /** «±N» в обход зон не имеет смысла: значение пересчитывается из зон */
    @Override
    public String getNewValue(String currentValue, Object inputValue) {
        return currentValue;
    }

    @Override
    public String getResetValue() {
        return "0";
    }

    /** Полностью здоров: все зоны целы, флаги сняты; набор зон и броня остаются */
    @Override
    public void reset(Counter counter) {
        counter.getAttributes().keySet().removeIf(key -> key.startsWith(LOST_PREFIX) || key.equals(AWAKE) || key.equals(DEAD));
        counter.setValue(0);
    }

    @Override
    public void prepareCopy(Counter counter) {
        reset(counter);
    }

    @Override
    public void initialize(Counter counter, Map<String, Integer> params) {
        counter.setValue(0);
        counter.getTexts().putIfAbsent(ZONES, KzBody.HUMAN.serialize());
    }

    /**
     * Правка мастером: {@code hp_<зона>} — текущее ХП, {@code armor_<зона>} — броня вручную, флаги {@link #AWAKE}, {@link #DEAD},
     * {@link #REST}.
     */
    @Override
    public void updateAttributes(Counter counter, Map<String, Integer> attributes) {
        KzBody body = body(counter);
        attributes.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            if (key.equals(AWAKE) || key.equals(DEAD)) {
                counter.setFlag(key, value != 0);
            } else if (key.equals(REST)) {
                counter.getAttributes().put(REST, Math.max(REST_NONE, Math.min(REST_FED, value)));
            } else if (key.startsWith(HP_PREFIX)) {
                KzBody.Zone zone = zone(body, key.substring(HP_PREFIX.length()));
                setLost(counter, zone, zone.maxHp() - Math.max(0, Math.min(zone.maxHp(), value)));
            } else if (key.startsWith(ARMOR_PREFIX)) {
                KzBody.Zone zone = zone(body, key.substring(ARMOR_PREFIX.length()));
                if (value <= 0) {
                    counter.getAttributes().remove(ARMOR_PREFIX + zone.code());
                } else {
                    counter.getAttributes().put(ARMOR_PREFIX + zone.code(), value);
                }
            } else {
                throw new IllegalArgumentException("Неизвестный параметр тела: " + key);
            }
        });
        recount(counter);
    }

    /**
     * Набор зон: {@code preset} — готовый («HUMAN», «BEAST», «ROBOT»), {@code zones} — свой текстом
     * (строка на зону: {@code название | цифры | ХП | множитель}, код — по порядку). Потери зон, которых больше нет, снимаются.
     */
    @Override
    public void updateTexts(Counter counter, Map<String, String> texts) {
        KzBody body;
        if (texts.get("preset") != null && !texts.get("preset").isBlank()) {
            body = KzBody.preset(texts.get("preset")).map(KzBody.Preset::body)
                    .orElseThrow(() -> new IllegalArgumentException("Нет такого набора зон: " + texts.get("preset")));
        } else if (texts.get(ZONES) != null) {
            body = parseEditor(texts.get(ZONES), body(counter));
        } else {
            throw new IllegalArgumentException("Нечего менять");
        }
        counter.getTexts().put(ZONES, body.serialize());
        counter.getAttributes().keySet().removeIf(key -> (key.startsWith(LOST_PREFIX) || key.startsWith(ARMOR_PREFIX))
                && body.zone(key.substring(key.indexOf('_') + 1)).isEmpty());
        // ХП зоны уменьшили — потеря не больше нового полного
        body.zones().forEach(zone -> setLost(counter, zone, Math.min(zone.maxHp(), lost(counter, zone))));
        recount(counter);
    }

    /**
     * Ввод мастера: строка на зону {@code Название | цифры | ХП | множитель}; множитель можно не писать.
     * Код зоны с тем же названием сохраняется (и её потери), новой — по порядку.
     */
    static KzBody parseEditor(String text, KzBody old) {
        List<KzBody.Zone> zones = new ArrayList<>();
        int index = 1;
        for (String raw : text.split("\\R")) {
            if (raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split("\\|", -1);
            if (parts.length < 3) {
                throw new IllegalArgumentException("Строка зоны — «Название | цифры | ХП | множитель»: " + raw.strip());
            }
            String name = parts[0].strip();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("У зоны нет названия: " + raw.strip());
            }
            int hp;
            int multiplier;
            try {
                hp = Integer.parseInt(parts[2].strip());
                multiplier = parts.length > 3 && !parts[3].isBlank() ? Integer.parseInt(parts[3].strip().replace("×", "").replace("x", "")) : 1;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("ХП и множитель — числа: " + raw.strip());
            }
            if (hp < 1 || multiplier < 1) {
                throw new IllegalArgumentException("ХП и множитель — не меньше 1: " + raw.strip());
            }
            String code = old.zones().stream().filter(zone -> zone.name().equalsIgnoreCase(name)).map(KzBody.Zone::code).findFirst()
                    .orElse("z" + index);
            zones.add(new KzBody.Zone(uniqueCode(code, zones), name, KzBody.digits(parts[1]), hp, multiplier));
            index++;
        }
        if (zones.isEmpty()) {
            throw new IllegalArgumentException("Нужна хотя бы одна зона");
        }
        return new KzBody(zones);
    }

    private static String uniqueCode(String code, List<KzBody.Zone> taken) {
        String candidate = code;
        int n = 2;
        while (true) {
            String check = candidate;
            if (taken.stream().noneMatch(zone -> zone.code().equals(check))) {
                return candidate;
            }
            candidate = code + n++;
        }
    }

    @Override
    public String performAction(CounterContext context, String action) {
        Counter counter = context.getCounter();
        switch (action) {
            case "AWAKE" -> {
                counter.setFlag(AWAKE, !counter.getFlag(AWAKE));
                return counter.getFlag(AWAKE) ? "В сознании" : "Снова без сознания";
            }
            case "DEAD" -> {
                counter.setFlag(DEAD, !counter.getFlag(DEAD));
                return counter.getFlag(DEAD) ? "Отмечен мёртвым" : "Снова жив";
            }
            default -> throw new IllegalArgumentException("Неизвестное действие: " + action);
        }
    }

    // ---------- HealthCounterStrategy ----------

    @Override
    public boolean isDead(CounterContext context) {
        return context.getCounter().getFlag(DEAD);
    }

    @Override
    public HealthEvent applyDamage(CounterContext context, DamageRequest request) {
        Counter counter = context.getCounter();
        if (request.getAmount() < 0) {
            throw new IllegalArgumentException("Урон не может быть отрицательным");
        }
        KzBody body = body(counter);
        HealthEvent event = new HealthEvent();
        boolean wasOut = unconscious(counter);
        KzBody.Zone zone = target(body, request, event);
        int hits = Math.max(1, request.extra(HITS, 1));
        int armor = armor(counter, zone);
        int effective = armor;
        StringBuilder line = new StringBuilder("Урон " + request.getAmount());
        if (armor > 0) {
            if (request.extra(ARMOR_HALF, 0) > 0) {
                // HOMEBREW: половина брони — вниз
                effective = armor / 2;
                line.append(" − броня ").append(armor).append(" (½ = ").append(effective).append(")");
            } else {
                line.append(" − броня ").append(armor);
            }
        }
        int perHit = Math.max(0, request.getAmount() - effective);
        if (armor > 0) {
            line.append(" = ").append(perHit);
        }
        event.step(line.toString());
        if (zone.multiplier() > 1 && perHit > 0) {
            event.step("× " + zone.multiplier() + " (" + zone.name() + ") = " + perHit * zone.multiplier());
            perHit *= zone.multiplier();
        }
        int total = perHit * hits;
        if (hits > 1) {
            event.step(perHit + " × " + hits + " попаданий = " + total);
        }
        int bonus = Math.max(0, request.extra(BONUS, 0));
        if (bonus > 0) {
            total += bonus;
            event.step("+ " + bonus + " за крит = " + total);
        }
        int hp = hp(counter, zone);
        if (hp <= 0) {
            event.step(zone.name() + " уже на нуле — урон пропадает");
            return event.summary(zone.name() + ": уже на нуле, урон пропал").chip("armor", "Зона на нуле");
        }
        if (total == 0) {
            event.step("Броня выдержала");
            return event.summary("Броня выдержала: " + zone.name()).chip("armor", "Броня выдержала");
        }
        int dealt = Math.min(hp, total);
        setLost(counter, zone, lost(counter, zone) + dealt);
        int now = hp - dealt;
        event.step(zone.name() + ": " + hp + " → " + now + " из " + zone.maxHp());
        recount(counter);
        event.chip("dmg", total + " урона · " + zone.name());
        if (now == 0) {
            // новая зона на нуле — снова без сознания, даже если мастер поднимал раньше
            counter.setFlag(AWAKE, false);
            if (!wasOut) {
                event.step(zone.name() + " на нуле — без сознания");
            }
            event.chip("bad", "Без сознания");
        }
        return event.summary(zone.name() + " −" + dealt + " (" + now + "/" + zone.maxHp() + ")" + (now == 0 ? ", без сознания" : ""));
    }

    /** Зона попадания: выбранная, по последней цифре броска или по броску к10 */
    private KzBody.Zone target(KzBody body, DamageRequest request, HealthEvent event) {
        if (request.getLocation() != null && !request.getLocation().isBlank()) {
            Optional<KzBody.Zone> chosen = body.zone(request.getLocation());
            if (chosen.isPresent()) {
                return chosen.get();
            }
            event.step("У цели нет зоны «" + request.getLocation() + "» — по цифре кубика");
        }
        int digit;
        if (request.getExtras().containsKey(DIGIT)) {
            digit = Math.floorMod(request.extra(DIGIT, 0), 10);
        } else {
            digit = dice.roll(10) % 10;
            event.step("Зона: к10 → " + digit);
        }
        int wanted = digit;
        Optional<KzBody.Zone> byDigit = body.byDigit(digit);
        if (byDigit.isPresent()) {
            event.step("Зона по цифре " + digit + ": " + byDigit.get().name());
            return byDigit.get();
        }
        // HOMEBREW: у существа нет зоны с такой цифрой — попадание в самую большую зону
        KzBody.Zone largest = body.zones().stream().max(Comparator.comparingInt(KzBody.Zone::maxHp)).orElseThrow();
        event.step("Цифре " + wanted + " зоны нет — в " + largest.name());
        return largest;
    }

    @Override
    public HealthEvent heal(CounterContext context, int points) {
        Counter counter = context.getCounter();
        HealthEvent event = new HealthEvent();
        int healed = healAll(counter, points);
        return event.summary(healed == 0 ? "Лечить нечего" : "+" + points + " в каждую зону, всего " + healed);
    }

    /** GM-01: мастер лечит зону с карты боя; без зоны — каждую */
    @Override
    public HealthEvent heal(CounterContext context, int points, String location) {
        return location == null ? heal(context, points) : healZone(context.getCounter(), location, points);
    }

    @Override
    public boolean healsByLocation() {
        return true;
    }

    /** Лечение одной зоны: «Жидкая кожа», правка мастера */
    public static HealthEvent healZone(Counter counter, String zoneCode, int points) {
        KzBody.Zone zone = zone(body(counter), zoneCode);
        int lost = lost(counter, zone);
        int healed = Math.min(lost, Math.max(0, points));
        setLost(counter, zone, lost - healed);
        recount(counter);
        return new HealthEvent().summary(zone.name() + " +" + healed + " (" + hp(counter, zone) + "/" + zone.maxHp() + ")");
    }

    /**
     * Каждой зоне + {@code points}, не выше полного.
     *
     * @return сколько вылечено всего
     */
    public static int healAll(Counter counter, int points) {
        int healed = 0;
        for (KzBody.Zone zone : body(counter).zones()) {
            int lost = lost(counter, zone);
            int part = Math.min(lost, Math.max(0, points));
            setLost(counter, zone, lost - part);
            healed += part;
        }
        recount(counter);
        return healed;
    }

    /** За день: сон — +30 в каждую зону, сон и еда — +60 */
    @Override
    public HealthEvent healOverTime(CounterContext context, int days) {
        Counter counter = context.getCounter();
        HealthEvent event = new HealthEvent();
        int rest = counter.getAttribute(REST, DEFAULT_REST);
        int perDay = rest == REST_FED ? HEAL_FED : rest == REST_SLEEP ? HEAL_SLEEP : 0;
        if (days <= 0 || perDay == 0 || counter.getFlag(DEAD) || counter.getValue() == null || counter.getValue() == 0) {
            return event;
        }
        int healed = healAll(counter, perDay * days);
        String who = counter.getCharacter() == null ? "" : counter.getCharacter().getName() + ": ";
        return event.summary(who + (rest == REST_FED ? "сон и еда" : "сон") + ", " + days + " дн. — +" + healed + " ХП");
    }

    @Override
    public List<Option> getRestOptions() {
        return REST_OPTIONS;
    }

    @Override
    public String getRest(Counter counter) {
        return String.valueOf(counter.getAttribute(REST, DEFAULT_REST));
    }

    @Override
    public void setRest(Counter counter, String rest) {
        updateAttributes(counter, Map.of(REST, Integer.parseInt(rest)));
    }

    @Override
    public HealthEvent save(CounterContext context, String saveType, Integer roll) {
        throw new UnsupportedOperationException("Спасбросков у Космозэков нет");
    }

    @Override
    public HealthEvent stabilize(CounterContext context) {
        throw new UnsupportedOperationException("Стабилизации у Космозэков нет: лечите зону");
    }

    @Override
    public KzBodyView describe(CounterContext context) {
        Counter counter = context.getCounter();
        KzBody body = body(counter);
        List<KzBodyView.ZoneRow> rows = new ArrayList<>();
        for (KzBody.Zone zone : body.zones()) {
            int hp = hp(counter, zone);
            int armor = armor(counter, zone);
            rows.add(new KzBodyView.ZoneRow(zone.code(), zone.name(), zone.digitsLabel(), hp, zone.maxHp(), armor,
                    armor == 0 ? null : armorSource(counter, zone), zone.multiplier(), zone.maxHp() == 0 ? 0 : hp * 100 / zone.maxHp(),
                    zone.digits().stream().sorted().toList()));
        }
        int max = body.zones().stream().mapToInt(KzBody.Zone::maxHp).sum();
        int lost = body.zones().stream().mapToInt(zone -> lost(counter, zone)).sum();
        String editor = body.zones().stream()
                .map(zone -> zone.name() + " | " + zone.digitsLabel().replace("—", "") + " | " + zone.maxHp() + " | " + zone.multiplier())
                .reduce((a, b) -> a + "\n" + b).orElse("");
        return new KzBodyView(lost, max, statusLabel(counter), statusCss(counter), counter.getFlag(DEAD), unconscious(counter),
                counter.getFlag(AWAKE) && anyDown(counter), counter.getAttribute(REST, DEFAULT_REST), rows, editor,
                KzBody.PRESETS.stream().map(preset -> new Option(preset.code(), preset.label())).toList(), REST_OPTIONS);
    }

    // ---------- состояние ----------

    public static KzBody body(Counter counter) {
        return KzBody.parse(counter.getTexts().get(ZONES));
    }

    private static KzBody.Zone zone(KzBody body, String code) {
        return body.zone(code).orElseThrow(() -> new IllegalArgumentException("Нет такой зоны: " + code));
    }

    static int lost(Counter counter, KzBody.Zone zone) {
        return Math.max(0, Math.min(zone.maxHp(), counter.getAttribute(LOST_PREFIX + zone.code(), 0)));
    }

    public static int hp(Counter counter, KzBody.Zone zone) {
        return zone.maxHp() - lost(counter, zone);
    }

    private static void setLost(Counter counter, KzBody.Zone zone, int lost) {
        if (lost <= 0) {
            counter.getAttributes().remove(LOST_PREFIX + zone.code());
        } else {
            counter.getAttributes().put(LOST_PREFIX + zone.code(), Math.min(zone.maxHp(), lost));
        }
    }

    private static void recount(Counter counter) {
        KzBody body = body(counter);
        counter.setValue(body.zones().stream().mapToInt(zone -> lost(counter, zone)).sum());
        if (!anyDown(counter)) {
            counter.setFlag(AWAKE, false);
        }
    }

    static boolean anyDown(Counter counter) {
        return body(counter).zones().stream().anyMatch(zone -> hp(counter, zone) <= 0);
    }

    /** Без сознания: хотя бы одна зона на нуле, и мастер не отметил «в сознании» */
    public static boolean unconscious(Counter counter) {
        return anyDown(counter) && !counter.getFlag(AWAKE);
    }

    /** Выбыл из боя: без сознания или мёртв */
    public static boolean out(Counter counter) {
        return counter.getFlag(DEAD) || unconscious(counter);
    }

    public static String statusLabel(Counter counter) {
        if (counter.getFlag(DEAD)) {
            return "Мёртв";
        }
        if (unconscious(counter)) {
            return "Без сознания";
        }
        // UX-26: «Цел» — служебное слово; как у CP2020 — «Без ранений»
        return counter.getValue() == null || counter.getValue() == 0 ? "Без ранений" : "Ранен";
    }

    private static String statusCss(Counter counter) {
        if (counter.getFlag(DEAD)) {
            return "dead";
        }
        if (unconscious(counter)) {
            return "out";
        }
        return counter.getValue() == null || counter.getValue() == 0 ? "ok" : "hurt";
    }

    /**
     * Броня зоны: на зоне действует одна броня — бо́льшая из надетых предметов и заданной вручную.
     */
    public static int armor(Counter counter, KzBody.Zone zone) {
        int manual = counter.getAttribute(ARMOR_PREFIX + zone.code(), 0);
        int worn = KzItems.wornArmor(counter.getCharacter())
                .filter(item -> KzItems.covers(item, zone.code()))
                .mapToInt(item -> KzItems.number(item, KzItems.ARMOR_VALUE, 0))
                .max().orElse(0);
        return Math.max(manual, worn);
    }

    private static String armorSource(Counter counter, KzBody.Zone zone) {
        int manual = counter.getAttribute(ARMOR_PREFIX + zone.code(), 0);
        Optional<Item> worn = KzItems.wornArmor(counter.getCharacter())
                .filter(item -> KzItems.covers(item, zone.code()))
                .max(Comparator.comparingInt(item -> KzItems.number(item, KzItems.ARMOR_VALUE, 0)));
        if (worn.isPresent() && KzItems.number(worn.get(), KzItems.ARMOR_VALUE, 0) >= manual) {
            return worn.get().getName();
        }
        return "вручную";
    }
}
