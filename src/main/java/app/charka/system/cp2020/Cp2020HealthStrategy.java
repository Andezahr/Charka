package app.charka.system.cp2020;

import app.charka.model.Item;
import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.AbstractCounterStrategy;
import app.charka.model.counters.strategy.CounterContext;
import app.charka.model.counters.strategy.health.CareOption;
import app.charka.model.counters.strategy.health.DamageRequest;
import app.charka.model.counters.strategy.health.DiceRoller;
import app.charka.model.counters.strategy.health.HealthCounterStrategy;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.system.cp2020.Cp2020HealthView.Option;
import app.charka.system.cp2020.Cp2020HealthView.TrackLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;

import static app.charka.system.cp2020.Cp2020Rules.*;

/**
 * Здоровье по правилам Cyberpunk 2020 (Friday Night Firefight).
 * <p>
 * Значение счётчика — полученный урон (0..40). BODY берётся из счётчика характеристик
 * ({@link Cp2020StatsStrategy}), SP — из счётчика брони ({@link Cp2020ArmorStrategy}); оба обязательны
 * и создаются вместе со здоровьем. Сам счётчик здоровья хранит в attributes только состояние тела:
 * {@code lost_<ЗОНА>} — потерянные конечности, флаги {@code stunned}, {@code stabilized}, {@code dead},
 * {@code deathSavePending}.
 * <p>
 * Порядок расчёта урона: зона попадания → минус SP (броня изнашивается на 1 при пробитии)
 * → ×2 в голову → минус BTM (не меньше 1) → проверка отрыва конечности / смерти → спасбросок от оглушения.
 */
@Component
@RequiredArgsConstructor
public class Cp2020HealthStrategy extends AbstractCounterStrategy implements HealthCounterStrategy {

    public static final String TYPE = "CP2020_HEALTH";
    /** Сколько SP снимает пробитие (`DamageRequest.extras`) */
    public static final String ARMOR_WEAR = "armorWear";

    public static final String STUNNED = "stunned";
    /** Больше 0 — BTM не вычитается: урон операции */
    public static final String NO_BTM = "noBtm";
    /** SP укрытия цели: {@link Cp2020Cover} */
    public static final String COVER_SP = "coverSp";
    /** Больше 0 — цель бросает спасбросок от яда, успех вдвое снижает урон */
    public static final String POISON_SAVE = "poisonSave";
    public static final String STABILIZED = "stabilized";
    public static final String DEAD = "dead";
    public static final String DEATH_SAVE_PENDING = "deathSavePending";
    public static final String LOST_PREFIX = "lost_";

    /** Медпомощь (с. 115–116): 0 — без лечения, 1 — First Aid, 2 — Medical Tech */
    public static final String CARE = "care";
    public static final String SPEEDHEAL = "speedheal";
    public static final String NANOTECH = "nanotech";
    /** Накопленные половинки очка: First Aid лечит 0.5 в день */
    public static final String HEAL_HALVES = "healHalves";
    static final int CARE_NONE = 0;
    static final int CARE_FIRST_AID = 1;
    static final int CARE_MEDICAL_TECH = 2;
    /** Без медицинской проверки: «2 points per day» (с. 116) */
    static final int UNTREATED_DAMAGE_PER_DAY = 2;

    private static final CareOption STABILIZE_MED = new CareOption("STABILIZE_MED", "Стабилизировать: Медицинская техника", "medical_tech", false, true);
    private static final CareOption STABILIZE_AID = new CareOption("STABILIZE_AID", "Стабилизировать: Первая помощь", "first_aid", false, true);
    private static final CareOption TREAT_MED = new CareOption("TREAT_MED", "Лечить: Медицинская техника", "medical_tech", false, false);
    private static final CareOption TREAT_AID = new CareOption("TREAT_AID", "Лечить: Первая помощь", "first_aid", true, false);
    private static final List<CareOption> CARE_OPTIONS = List.of(STABILIZE_MED, STABILIZE_AID, TREAT_MED, TREAT_AID);

    /** Прибавки к броску стабилизации (с. 116, с изображения страницы) */
    private static final List<Option> CARE_MODIFIERS = List.of(
            new Option("0", "Без условий"),
            new Option("5", "Больница +5"),
            new Option("3", "Trauma Team +3"),
            new Option("3", "Cryo Tank +3"));

    public static final String SAVE_STUN = "STUN";
    public static final String SAVE_DEATH = "DEATH";

    private static final Set<String> CARE_KEYS = Set.of(CARE, SPEEDHEAL, NANOTECH, HEAL_HALVES);
    private static final String STATS_NAME = "Характеристики";
    private static final String ARMOR_NAME = "Броня";

    private final DiceRoller dice;
    private final Cp2020ArmorStrategy armorStrategy;

    // ---------- CounterStrategy ----------

    @Override
    public String getCounterType() {
        return TYPE;
    }

    /** Копия начинает без ран */
    @Override
    public void prepareCopy(Counter counter) {
        reset(counter);
    }

    @Override
    public String getDisplayName() {
        return "Здоровье (Cyberpunk 2020)";
    }

    @Override
    public String getDefaultName() {
        return "Здоровье";
    }

    @Override
    public String getViewTemplate() {
        return "counters/types/cp2020-health";
    }

    /** R-05: строка на листе — тот же шаблон, фрагмент {@code card-compact} */
    @Override
    public String getCompactTemplate() {
        return "counters/types/cp2020-health";
    }

    @Override
    public String getCreateFieldsTemplate() {
        return "counters/types/cp2020-health";
    }

    @Override
    public List<String> getRequiredCounterTypes() {
        return List.of(Cp2020StatsStrategy.TYPE, Cp2020ArmorStrategy.TYPE);
    }

    @Override
    public boolean isUniquePerCharacter() {
        return true;
    }

    @Override
    public int getDisplayOrder() {
        return 10;
    }

    @Override
    public Set<String> getSystems() {
        return Set.of(Cp2020System.CODE);
    }

    /**
     * «±N» — урон напрямую, без брони и BTM (для исправлений вручную).
     */
    @Override
    public String getNewValue(String currentValue, Object inputValue) {
        return String.valueOf(Math.max(0, Integer.parseInt(currentValue) + (int) inputValue));
    }

    @Override
    public String getResetValue() {
        return "0";
    }

    /**
     * Полное исцеление: урон 0, снимаются все состояния и потери конечностей.
     */
    @Override
    public void reset(Counter counter) {
        counter.setValue(0);
        counter.getAttributes().keySet().removeIf(key ->
                key.startsWith(LOST_PREFIX)
                        || key.equals(STUNNED) || key.equals(STABILIZED)
                        || key.equals(DEAD) || key.equals(DEATH_SAVE_PENDING) || CARE_KEYS.contains(key));
    }

    /** Начальное значение — уже полученный урон; характеристики из формы забирает счётчик характеристик */
    @Override
    public void initialize(Counter counter, Map<String, Integer> params) {
        counter.setValue(Math.max(0, counter.getValue() == null ? 0 : counter.getValue()));
    }

    /** Редактируются потеря конечностей ({@code lost_<ЗОНА>} = 0/1) и ускорители лечения ({@code speedheal}, {@code nanotech}) */
    @Override
    public void updateAttributes(Counter counter, Map<String, Integer> attributes) {
        attributes.forEach((key, value) -> {
            if (key.equals(SPEEDHEAL) || key.equals(NANOTECH)) {
                counter.setFlag(key, value != null && value != 0);
                return;
            }
            if (!key.startsWith(LOST_PREFIX)) {
                throw new IllegalArgumentException("Неизвестный параметр здоровья: " + key);
            }
            Cp2020Location location = Cp2020Location.parse(key.substring(LOST_PREFIX.length()));
            if (value != null) {
                counter.setFlag(lostKey(location), value != 0);
            }
        });
    }

    // ---------- HealthCounterStrategy ----------

    @Override
    public HealthEvent applyDamage(CounterContext context, DamageRequest request) {
        Counter counter = context.getCounter();
        ensureAlive(counter);
        if (request.getAmount() < 0) {
            throw new IllegalArgumentException("Урон не может быть отрицательным");
        }
        Counter armor = context.requireSibling(Cp2020ArmorStrategy.TYPE, ARMOR_NAME);
        HealthEvent event = new HealthEvent();
        Cp2020DamageType type = Cp2020DamageType.parse(request.getDamageType());
        Cp2020Location location = resolveLocation(request.getLocation(), event);

        // 1. Броня
        int amount = request.getAmount();
        if (request.extra(POISON_SAVE, 0) > 0) {
            // HOMEBREW: книга (с. 107, 110) говорит «save», не называя числа; взят обычный спасбросок от BODY (с. 25)
            int roll = dice.d10();
            int target = body(context);
            boolean saved = roll <= target;
            event.step("Спасбросок от яда: d10 = " + roll + ", нужно ≤ " + target + (saved ? " — успех, половина урона" : " — провал"));
            amount = saved ? Math.max(1, amount / 2) : amount;
        }
        int sp = armorStrategy.sp(armor, location);
        int armorSp = sp;
        if (type == Cp2020DamageType.BLADE || type == Cp2020DamageType.MONO) {
            armorSp = armorStrategy.edgeSp(armor, location, type == Cp2020DamageType.MONO);
            if (armorSp != sp) {
                event.step(type.getLabel() + ": SP " + sp + " считается за " + armorSp);
            }
        }
        boolean softAgainstFlame = type == Cp2020DamageType.FLAME && !armorStrategy.isHard(armor, location);
        if (softAgainstFlame && sp > 0) {
            if (sp <= Cp2020Rules.SOFT_ARMOR_FLAME_LIMIT) {
                armorSp = 0;
                event.step("Мягкая броня до " + Cp2020Rules.SOFT_ARMOR_FLAME_LIMIT + " SP от огня не защищает");
            }
            int burned = sp;
            for (int i = 0; i < Cp2020Rules.SOFT_ARMOR_FLAME_WEAR; i++) {
                burned = armorStrategy.wear(armor, location);
            }
            event.step("Огонь портит мягкую броню: SP «" + location.getLabel() + "» " + sp + " → " + burned);
        }
        // Укрытие складывается с бронёй как слой (с. 101). HOMEBREW: оно не изнашивается, книга молчит
        int coverSp = type == Cp2020DamageType.BYPASS ? 0 : request.extra(COVER_SP, 0);
        int shielding = coverSp > 0 ? Cp2020ArmorRules.combine(List.of(coverSp, armorSp)) : armorSp;
        if (coverSp > 0) {
            event.step("Укрытие SP " + coverSp + " и броня SP " + armorSp + " → SP " + shielding);
        }
        int effectiveSp = type.effectiveSp(shielding);
        int damage = amount - effectiveSp;
        if (type == Cp2020DamageType.BYPASS) {
            event.step("В обход брони: урон " + amount);
        } else {
            String spText = effectiveSp != shielding
                    ? "SP " + effectiveSp + " (половина от " + shielding + ")"
                    : "SP " + effectiveSp;
            event.step("Урон " + amount + " − " + spText + " = " + Math.max(damage, 0));
        }
        if (damage <= 0) {
            event.chip("armor", "Не пробил · " + location.getLabel() + ", SP " + shielding);
            return event.summary(who(counter) + ": " + (coverSp > 0 ? "укрытие и броня выдержали" : "броня выдержала") + " (" + location.getLabel() + ", SP " + shielding + ")");
        }
        if (shielding > 0 && type != Cp2020DamageType.BYPASS) {
            event.chip("pierce", "Пробил SP " + shielding);
        }
        if (type == Cp2020DamageType.ARMOR_PIERCING || (type == Cp2020DamageType.SLUG && !armorStrategy.isHard(armor, location))) {
            damage = Math.max(1, damage / 2);
            event.step("Бронебойный: прошедший урон делится пополам → " + damage);
        }
        if (type != Cp2020DamageType.BYPASS && sp > 0 && !softAgainstFlame) {
            int worn = sp;
            for (int i = 0; i < request.extra(ARMOR_WEAR, 1); i++) {
                worn = armorStrategy.wear(armor, location);
            }
            event.step("Броня пробита и изношена: SP «" + location.getLabel() + "» " + sp + " → " + worn);
        }

        // Киберконечность принимает урон сама, как машина (с. 89): тело не ранено
        Optional<Item> cyberlimb = location.isLimb() ? Cp2020Cyberlimbs.at(counter.getCharacter(), location) : Optional.empty();
        if (cyberlimb.isPresent()) {
            return event.summary(Cp2020Cyberlimbs.hit(cyberlimb.get(), damage, event::step));
        }

        // 2. Голова
        if (location == Cp2020Location.HEAD) {
            damage *= 2;
            event.step("Попадание в голову: ×2 → " + damage);
        }

        // 3. Телосложение
        int btm = request.extra(NO_BTM, 0) > 0 ? 0 : btm(body(context));
        int afterBtm = damage - btm;
        damage = Math.max(1, afterBtm);
        event.step("BTM −" + btm + " → " + damage + (afterBtm < 1 ? " (минимум 1)" : ""));

        int before = counter.getValue();
        counter.setValue(before + damage);
        counter.setFlag(STABILIZED, false);
        event.step("Урон на треке: " + before + " → " + counter.getValue() + " из " + MAX_DAMAGE);
        String summary = who(counter) + " получает " + damage + " урона (" + location.getLabel() + ")";
        event.chip("dmg", damage + " урона · " + location.getLabel());

        // 4. Смертельные попадания
        if (location == Cp2020Location.HEAD && damage > SEVERE_HIT_THRESHOLD) {
            counter.setFlag(DEAD, true);
            event.step("Больше " + SEVERE_HIT_THRESHOLD + " урона в голову — мгновенная смерть");
            event.chip("dead", "Убит в голову");
            return event.summary(summary + " — убит выстрелом в голову");
        }
        if (location.isLimb() && damage > SEVERE_HIT_THRESHOLD && !counter.getFlag(lostKey(location))) {
            counter.setFlag(lostKey(location), true);
            counter.setFlag(DEATH_SAVE_PENDING, true);
            event.step("Больше " + SEVERE_HIT_THRESHOLD + " урона в конечность — «" + location.getLabel()
                    + "» оторвана или раздроблена. Нужен спасбросок от смерти (Смертельное 0)");
            event.chip("bad", location.getLabel() + " потеряна");
        }
        if (counter.getValue() > MAX_DAMAGE) {
            counter.setFlag(DEAD, true);
            event.step("Урон превысил " + MAX_DAMAGE + " — смерть");
            event.chip("dead", "Погиб");
            return event.summary(summary + " — погиб");
        }

        int level = woundLevel(counter.getValue());
        event.step("Состояние: " + levelName(level));
        event.chip("info", levelName(level));

        // 5. Оглушение
        if (request.isAutoSave()) {
            rollStunSave(context, null, 0, 0, event);
        }
        return event.summary(summary + ". " + statusLabel(counter));
    }

    @Override
    public HealthEvent heal(CounterContext context, int points) {
        Counter counter = context.getCounter();
        ensureAlive(counter);
        if (points <= 0) {
            throw new IllegalArgumentException("Количество лечения должно быть положительным");
        }
        int before = counter.getValue();
        int after = Math.max(0, before - points);
        counter.setValue(after);
        if (!isMortal(woundLevel(after))) {
            counter.setFlag(STABILIZED, false);
            counter.setFlag(DEATH_SAVE_PENDING, false);
        }
        return new HealthEvent()
                .step("Урон на треке: " + before + " → " + after)
                .summary(who(counter) + ": вылечено " + (before - after) + ". " + statusLabel(counter));
    }

    /**
     * Естественное восстановление при отдыхе. Мёртвые и умирающие без стабилизации не восстанавливаются.
     */
    /**
     * Прошли дни (с. 116). Без успешной проверки медицины раненый теряет 2 очка в день, а смертельно раненый ещё и
     * бросает спасбросок от смерти каждый день. После проверки: First Aid 0.5 в день, Medical Tech 1,
     * Speedheal и нанотехника +1 каждый. Умирающего сначала стабилизируют: без этого лечение не идёт.
     */
    @Override
    public HealthEvent healOverTime(CounterContext context, int days) {
        Counter counter = context.getCounter();
        HealthEvent event = new HealthEvent();
        if (isDead(counter) || counter.getValue() == 0 || days <= 0) {
            return event;
        }
        int care = counter.getAttribute(CARE, CARE_NONE);
        boolean dyingUnstabilized = isMortal(woundLevel(counter.getValue())) && !counter.getFlag(STABILIZED);
        int before = counter.getValue();
        if (care == CARE_NONE || dyingUnstabilized) {
            return untreated(context, days, event, before);
        }
        int halves = counter.getAttribute(HEAL_HALVES, 0) + days * healingHalves(counter);
        int after = Math.max(0, before - halves / 2);
        counter.setValue(after);
        counter.getAttributes().put(HEAL_HALVES, halves % 2);
        if (after == 0) {
            counter.getAttributes().keySet().removeAll(CARE_KEYS);
            counter.setFlag(STABILIZED, false);
        }
        return event.summary(who(counter) + ": " + careLabel(counter, care) + ", за " + days + " дн. урон " + before + " → " + after);
    }

    private HealthEvent untreated(CounterContext context, int days, HealthEvent event, int before) {
        Counter counter = context.getCounter();
        int saves = 0;
        for (int day = 1; day <= days; day++) {
            counter.setValue(counter.getValue() + UNTREATED_DAMAGE_PER_DAY);
            if (counter.getValue() > MAX_DAMAGE) {
                counter.setFlag(DEAD, true);
                return event.summary(who(counter) + ": без лечения урон " + before + " → " + counter.getValue() + ", умер на " + day + "-й день");
            }
            if (isMortal(woundLevel(counter.getValue()))) {
                int target = deathTarget(body(context), counter.getValue());
                int roll = dice.d10();
                saves++;
                event.step("День " + day + ": спасбросок от смерти d10 = " + roll + ", нужно ≤ " + target);
                if (roll > target) {
                    counter.setFlag(DEAD, true);
                    return event.summary(who(counter) + ": без лечения урон " + before + " → " + counter.getValue()
                            + ", умер на " + day + "-й день (спасбросок от смерти)");
                }
            }
        }
        return event.summary(who(counter) + ": без лечения урон " + before + " → " + counter.getValue()
                + " (+" + UNTREATED_DAMAGE_PER_DAY + " в день" + (saves > 0 ? ", спасбросков от смерти: " + saves : "") + ")");
    }

    /** В отчёте о промотке — имя персонажа, а не счётчика */
    private static String who(Counter counter) {
        return counter.getCharacter() != null ? counter.getCharacter().getName() : counter.getName();
    }

    /** Скорость в половинках очка в день */
    private static int healingHalves(Counter counter) {
        if (counter.getAttribute(CARE, CARE_NONE) == CARE_FIRST_AID) {
            return 1;
        }
        return 2 + (counter.getFlag(SPEEDHEAL) ? 2 : 0) + (counter.getFlag(NANOTECH) ? 2 : 0);
    }

    private static String careLabel(Counter counter, int care) {
        if (care == CARE_NONE) {
            return "без лечения: +" + UNTREATED_DAMAGE_PER_DAY + " урона в день";
        }
        if (care == CARE_FIRST_AID) {
            return "Первая помощь: 0.5 в день";
        }
        String extras = (counter.getFlag(SPEEDHEAL) ? " + Speedheal" : "") + (counter.getFlag(NANOTECH) ? " + нанотехника" : "");
        return "Медицинская техника" + extras + ": " + healingHalves(counter) / 2 + " в день";
    }

    @Override
    public List<CareOption> getCareOptions() {
        return CARE_OPTIONS;
    }

    /**
     * Стабилизация: TECH + навык + 1D10 ≥ всего урона (с. 116); лечение: больше всего урона.
     */
    @Override
    public Integer careDifficulty(CounterContext context, CareOption option) {
        Counter counter = context.getCounter();
        if (isDead(counter) || counter.getValue() == 0) {
            return null;
        }
        if (option.stabilization()) {
            return deathSaveRequired(counter) ? counter.getValue() : null;
        }
        int level = option == TREAT_MED ? CARE_MEDICAL_TECH : CARE_FIRST_AID;
        // Medical Tech заменяет First Aid, обратно не бывает (с. 116)
        return counter.getAttribute(CARE, CARE_NONE) >= level ? null : counter.getValue() + 1;
    }

    @Override
    public HealthEvent applyCare(CounterContext context, CareOption option, boolean success) {
        Counter counter = context.getCounter();
        if (option.stabilization()) {
            return success ? stabilize(context)
                    : new HealthEvent().summary(who(counter) + ": стабилизировать не удалось, спасброски от смерти продолжаются");
        }
        if (!success) {
            return new HealthEvent().summary(who(counter) + ": лечение не удалось, новая попытка — через день");
        }
        int level = option == TREAT_MED ? CARE_MEDICAL_TECH : CARE_FIRST_AID;
        counter.getAttributes().put(CARE, Math.max(level, counter.getAttribute(CARE, CARE_NONE)));
        return new HealthEvent().summary(who(counter) + ": " + careLabel(counter, counter.getAttribute(CARE, CARE_NONE)));
    }

    @Override
    public HealthEvent save(CounterContext context, String saveType, Integer roll) {
        return save(context, saveType, roll, 0);
    }

    @Override
    public HealthEvent save(CounterContext context, String saveType, Integer roll, int modifier) {
        return save(context, saveType, roll, modifier, 0);
    }

    /**
     * HOMEBREW: спасбросок бросают «не выше» числа, поэтому удача (с. 26: прибавить к важному броску) прибавляется к порогу, а не к кубику.
     */
    @Override
    public HealthEvent save(CounterContext context, String saveType, Integer roll, int modifier, int luck) {
        ensureAlive(context.getCounter());
        if (roll != null && (roll < 1 || roll > 10)) {
            throw new IllegalArgumentException("Бросок d10 должен быть от 1 до 10");
        }
        HealthEvent event = new HealthEvent();
        String type = saveType == null ? "" : saveType.trim().toUpperCase();
        return switch (type) {
            case SAVE_STUN -> rollStunSave(context, roll, modifier, luck, event);
            case SAVE_DEATH -> rollDeathSave(context, roll, luck, event);
            default -> throw new IllegalArgumentException("Неизвестный спасбросок: " + saveType);
        };
    }

    @Override
    public HealthEvent stabilize(CounterContext context) {
        Counter counter = context.getCounter();
        ensureAlive(counter);
        if (!deathSaveRequired(counter)) {
            throw new IllegalStateException("Стабилизация нужна только смертельно раненым");
        }
        counter.setFlag(STABILIZED, true);
        counter.setFlag(DEATH_SAVE_PENDING, false);
        return new HealthEvent()
                .summary(who(counter) + " стабилизирован — спасброски от смерти больше не нужны");
    }

    @Override
    public Cp2020HealthView describe(CounterContext context) {
        Counter counter = context.getCounter();
        int damage = counter.getValue() == null ? 0 : counter.getValue();
        int level = woundLevel(damage);
        boolean dead = isDead(counter);
        // карточка не должна падать, даже если счётчика характеристик нет
        int body = context.sibling(Cp2020StatsStrategy.TYPE)
                .map(Cp2020StatsStrategy::body)
                .orElse(Cp2020StatsStrategy.DEFAULT_STAT);
        boolean deathSaveRequired = !dead && deathSaveRequired(counter);

        List<TrackLevel> track = new ArrayList<>();
        for (int i = 0; i < LEVEL_COUNT; i++) {
            int filled = Math.clamp(damage - i * BOXES_PER_LEVEL, 0, BOXES_PER_LEVEL);
            track.add(new TrackLevel(levelName(i), levelCss(i), filled));
        }

        return Cp2020HealthView.builder()
                .damage(damage)
                .maxDamage(MAX_DAMAGE)
                .statusLabel(statusLabel(counter))
                .statusCss(dead ? "dead" : levelCss(level))
                .dead(dead)
                .stunned(!dead && counter.getFlag(STUNNED))
                .stabilized(!dead && counter.getFlag(STABILIZED))
                .deathSaveRequired(deathSaveRequired)
                .body(body)
                .btm(btm(body))
                .stunTarget(body - stunPenalty(level))
                .deathTarget(deathSaveRequired ? deathTarget(body, damage) : null)
                .penalty(dead ? null : penalty(level))
                .track(track)
                .lostLimbs(Arrays.stream(Cp2020Location.values())
                        .filter(l -> counter.getFlag(lostKey(l)))
                        .map(l -> new Option(l.name(), l.getLabel()))
                        .toList())
                .locations(Arrays.stream(Cp2020Location.values())
                        .map(l -> new Option(l.name(), l.getLabel()))
                        .toList())
                .damageTypes(Arrays.stream(Cp2020DamageType.values())
                        .map(t -> new Option(t.name(), t.getLabel()))
                        .toList())
                .careLabel(dead || damage == 0 ? null : careLabel(counter, counter.getAttribute(CARE, CARE_NONE)))
                .medicalTech(counter.getAttribute(CARE, CARE_NONE) == CARE_MEDICAL_TECH)
                .speedheal(counter.getFlag(SPEEDHEAL))
                .nanotech(counter.getFlag(NANOTECH))
                .careOptions(CARE_OPTIONS.stream()
                        .filter(option -> careDifficulty(context, option) != null)
                        .map(option -> new Option(option.code(), option.label()))
                        .toList())
                .careModifiers(CARE_MODIFIERS)
                .build();
    }

    // ---------- спасброски ----------

    /** Спасбросок от оглушения: d10 ≤ BODY − штраф уровня ранения */
    private HealthEvent rollStunSave(CounterContext context, Integer roll, int modifier, int luck, HealthEvent event) {
        Counter counter = context.getCounter();
        int target = body(context) - stunPenalty(woundLevel(counter.getValue())) + modifier + luck;
        int result = roll != null ? roll : dice.d10();
        boolean success = result <= target;
        event.step("Спасбросок от оглушения" + (modifier != 0 ? " с " + modifier : "") + (luck > 0 ? ", Удача +" + luck : "")
                + ": d10 = " + result + ", нужно ≤ " + target
                + (success ? " — успех" : " — провал"));
        boolean wasStunned = counter.getFlag(STUNNED);
        // спасбросок с модификатором — от нового удара: уже оглушённого он в себя не приводит
        boolean staysStunned = wasStunned && modifier != 0;
        counter.setFlag(STUNNED, !success || staysStunned);
        String outcome;
        if (staysStunned && success) {
            outcome = " всё ещё оглушён";
            event.chip("bad", "Всё ещё оглушён");
        } else if (success) {
            outcome = wasStunned ? " приходит в себя" : " держится на ногах";
            event.chip("ok", wasStunned ? "Пришёл в себя" : "Устоял: не оглушён");
        } else {
            outcome = " оглушён и выведен из строя";
            event.chip("bad", "Оглушён");
        }
        return event.summary(who(counter) + outcome);
    }

    /** Спасбросок от смерти: d10 ≤ BODY − номер смертельного уровня */
    private HealthEvent rollDeathSave(CounterContext context, Integer roll, int luck, HealthEvent event) {
        Counter counter = context.getCounter();
        if (!deathSaveRequired(counter)) {
            throw new IllegalStateException("Спасбросок от смерти нужен только смертельно раненым без стабилизации");
        }
        int target = deathTarget(body(context), counter.getValue()) + luck;
        int result = roll != null ? roll : dice.d10();
        boolean success = result <= target;
        event.step("Спасбросок от смерти" + (luck > 0 ? ", Удача +" + luck : "") + ": d10 = " + result + ", нужно ≤ " + target
                + (success ? " — успех" : " — провал"));
        counter.setFlag(DEATH_SAVE_PENDING, false);
        if (!success) {
            counter.setFlag(DEAD, true);
            event.chip("dead", "Умер: спасбросок от смерти провален");
            return event.summary(who(counter) + " умирает");
        }
        event.chip("ok", "Выжил: спасбросок от смерти");
        return event.summary(who(counter) + " цепляется за жизнь");
    }

    // ---------- вспомогательное ----------

    private int body(CounterContext context) {
        return Cp2020StatsStrategy.body(context.requireSibling(Cp2020StatsStrategy.TYPE, STATS_NAME));
    }

    private boolean deathSaveRequired(Counter counter) {
        boolean mortal = isMortal(woundLevel(counter.getValue()));
        return counter.getFlag(DEATH_SAVE_PENDING) || (mortal && !counter.getFlag(STABILIZED));
    }

    private static int deathTarget(int body, int damage) {
        return body - Math.max(0, mortalLevel(woundLevel(damage)));
    }

    @Override
    public boolean isDead(CounterContext context) {
        return isDead(context.getCounter());
    }

    private boolean isDead(Counter counter) {
        return counter.getFlag(DEAD) || (counter.getValue() != null && counter.getValue() > MAX_DAMAGE);
    }

    private void ensureAlive(Counter counter) {
        if (isDead(counter)) {
            throw new IllegalStateException(counter.getName() + ": персонаж мёртв");
        }
    }

    private String statusLabel(Counter counter) {
        if (isDead(counter)) {
            return "Мёртв";
        }
        int level = woundLevel(counter.getValue());
        return level < 0 ? "Без ранений" : levelName(level) + " ранение";
    }

    private String penalty(int level) {
        if (level == 1) return "REF −2";
        if (level == 2) return "REF, INT, COOL ÷2";
        if (isMortal(level)) return "REF, INT, COOL ÷3";
        return null;
    }

    private Cp2020Location resolveLocation(String code, HealthEvent event) {
        if (code == null || code.isBlank()) {
            int roll = dice.d10();
            Cp2020Location location = Cp2020Location.fromRoll(roll);
            event.step("Зона попадания: d10 = " + roll + " → " + location.getLabel());
            return location;
        }
        Cp2020Location location = Cp2020Location.parse(code);
        event.step("Зона попадания: " + location.getLabel());
        return location;
    }

    private static String lostKey(Cp2020Location location) {
        return LOST_PREFIX + location.name();
    }
}
