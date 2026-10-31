package app.charka.system.cp2020;

import app.charka.model.counters.Counter;
import app.charka.model.counters.strategy.AbstractCounterStrategy;
import app.charka.model.counters.strategy.CounterContext;
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
    /** SP укрытия цели: {@link Cp2020Cover} */
    public static final String COVER_SP = "coverSp";
    /** Больше 0 — цель бросает спасбросок от яда, успех вдвое снижает урон */
    public static final String POISON_SAVE = "poisonSave";
    public static final String STABILIZED = "stabilized";
    public static final String DEAD = "dead";
    public static final String DEATH_SAVE_PENDING = "deathSavePending";
    public static final String LOST_PREFIX = "lost_";

    public static final String SAVE_STUN = "STUN";
    public static final String SAVE_DEATH = "DEATH";

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
                        || key.equals(DEAD) || key.equals(DEATH_SAVE_PENDING));
    }

    /** Начальное значение — уже полученный урон; характеристики из формы забирает счётчик характеристик */
    @Override
    public void initialize(Counter counter, Map<String, Integer> params) {
        counter.setValue(Math.max(0, counter.getValue() == null ? 0 : counter.getValue()));
    }

    /** Редактируется только потеря конечностей: {@code lost_<ЗОНА>} = 0/1 */
    @Override
    public void updateAttributes(Counter counter, Map<String, Integer> attributes) {
        attributes.forEach((key, value) -> {
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
            return event.summary(counter.getName() + ": " + (coverSp > 0 ? "укрытие и броня выдержали" : "броня выдержала") + " (" + location.getLabel() + ", SP " + shielding + ")");
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

        // 2. Голова
        if (location == Cp2020Location.HEAD) {
            damage *= 2;
            event.step("Попадание в голову: ×2 → " + damage);
        }

        // 3. Телосложение
        int btm = btm(body(context));
        int afterBtm = damage - btm;
        damage = Math.max(1, afterBtm);
        event.step("BTM −" + btm + " → " + damage + (afterBtm < 1 ? " (минимум 1)" : ""));

        int before = counter.getValue();
        counter.setValue(before + damage);
        counter.setFlag(STABILIZED, false);
        event.step("Урон на треке: " + before + " → " + counter.getValue() + " из " + MAX_DAMAGE);
        String summary = counter.getName() + " получает " + damage + " урона (" + location.getLabel() + ")";

        // 4. Смертельные попадания
        if (location == Cp2020Location.HEAD && damage > SEVERE_HIT_THRESHOLD) {
            counter.setFlag(DEAD, true);
            event.step("Больше " + SEVERE_HIT_THRESHOLD + " урона в голову — мгновенная смерть");
            return event.summary(summary + " — убит выстрелом в голову");
        }
        if (location.isLimb() && damage > SEVERE_HIT_THRESHOLD && !counter.getFlag(lostKey(location))) {
            counter.setFlag(lostKey(location), true);
            counter.setFlag(DEATH_SAVE_PENDING, true);
            event.step("Больше " + SEVERE_HIT_THRESHOLD + " урона в конечность — «" + location.getLabel()
                    + "» оторвана или раздроблена. Нужен спасбросок от смерти (Смертельное 0)");
        }
        if (counter.getValue() > MAX_DAMAGE) {
            counter.setFlag(DEAD, true);
            event.step("Урон превысил " + MAX_DAMAGE + " — смерть");
            return event.summary(summary + " — погиб");
        }

        int level = woundLevel(counter.getValue());
        event.step("Состояние: " + levelName(level));

        // 5. Оглушение
        if (request.isAutoSave()) {
            rollStunSave(context, null, 0, event);
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
                .summary(counter.getName() + ": вылечено " + (before - after) + ". " + statusLabel(counter));
    }

    /**
     * Естественное восстановление при отдыхе. Мёртвые и умирающие без стабилизации не восстанавливаются.
     */
    @Override
    public HealthEvent healOverTime(CounterContext context, int days) {
        Counter counter = context.getCounter();
        HealthEvent event = new HealthEvent();
        if (isDead(counter) || counter.getValue() == 0 || days <= 0) {
            return event;
        }
        if (isMortal(woundLevel(counter.getValue())) && !counter.getFlag(STABILIZED)) {
            return event.summary(counter.getName() + ": умирает без стабилизации, раны не заживают");
        }
        int before = counter.getValue();
        int after = Math.max(0, before - days * NATURAL_HEALING_PER_DAY);
        counter.setValue(after);
        return event.summary(counter.getName() + ": за " + days + " дн. урон " + before + " → " + after);
    }

    @Override
    public HealthEvent save(CounterContext context, String saveType, Integer roll) {
        return save(context, saveType, roll, 0);
    }

    @Override
    public HealthEvent save(CounterContext context, String saveType, Integer roll, int modifier) {
        ensureAlive(context.getCounter());
        if (roll != null && (roll < 1 || roll > 10)) {
            throw new IllegalArgumentException("Бросок d10 должен быть от 1 до 10");
        }
        HealthEvent event = new HealthEvent();
        String type = saveType == null ? "" : saveType.trim().toUpperCase();
        return switch (type) {
            case SAVE_STUN -> rollStunSave(context, roll, modifier, event);
            case SAVE_DEATH -> rollDeathSave(context, roll, event);
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
                .summary(counter.getName() + " стабилизирован — спасброски от смерти больше не нужны");
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
                .build();
    }

    // ---------- спасброски ----------

    /** Спасбросок от оглушения: d10 ≤ BODY − штраф уровня ранения */
    private HealthEvent rollStunSave(CounterContext context, Integer roll, int modifier, HealthEvent event) {
        Counter counter = context.getCounter();
        int target = body(context) - stunPenalty(woundLevel(counter.getValue())) + modifier;
        int result = roll != null ? roll : dice.d10();
        boolean success = result <= target;
        event.step("Спасбросок от оглушения" + (modifier != 0 ? " с " + modifier : "") + ": d10 = " + result + ", нужно ≤ " + target
                + (success ? " — успех" : " — провал"));
        boolean wasStunned = counter.getFlag(STUNNED);
        // спасбросок с модификатором — от нового удара: уже оглушённого он в себя не приводит
        boolean staysStunned = wasStunned && modifier != 0;
        counter.setFlag(STUNNED, !success || staysStunned);
        String outcome;
        if (staysStunned && success) {
            outcome = " всё ещё оглушён";
        } else if (success) {
            outcome = wasStunned ? " приходит в себя" : " держится на ногах";
        } else {
            outcome = " оглушён и выведен из строя";
        }
        return event.summary(counter.getName() + outcome);
    }

    /** Спасбросок от смерти: d10 ≤ BODY − номер смертельного уровня */
    private HealthEvent rollDeathSave(CounterContext context, Integer roll, HealthEvent event) {
        Counter counter = context.getCounter();
        if (!deathSaveRequired(counter)) {
            throw new IllegalStateException("Спасбросок от смерти нужен только смертельно раненым без стабилизации");
        }
        int target = deathTarget(body(context), counter.getValue());
        int result = roll != null ? roll : dice.d10();
        boolean success = result <= target;
        event.step("Спасбросок от смерти: d10 = " + result + ", нужно ≤ " + target
                + (success ? " — успех" : " — провал"));
        counter.setFlag(DEATH_SAVE_PENDING, false);
        if (!success) {
            counter.setFlag(DEAD, true);
            return event.summary(counter.getName() + " умирает");
        }
        return event.summary(counter.getName() + " цепляется за жизнь");
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
