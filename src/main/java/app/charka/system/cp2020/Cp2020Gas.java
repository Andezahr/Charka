package app.charka.system.cp2020;

import app.charka.effect.EffectTemplate.DurationUnit;
import app.charka.effect.TimedEffects;
import app.charka.model.Character;
import app.charka.model.counters.strategy.health.DiceRoller;

import java.util.Map;

/**
 * Несмертельные газы (с. 107, 110): спасбросок, успех — половина эффекта; эффект — временный штраф к стату на листе.
 */
final class Cp2020Gas {

    static final String HALLUCINOGEN = "GAS_HALLUCINOGEN";
    static final String NAUSEA = "GAS_NAUSEA";
    static final String TEARGAS = "GAS_TEARGAS";
    static final String SLEEP = "GAS_SLEEP";

    /** С. 82: Toxin Binders «+4 to saves vs. poisons and drugs» */
    private static final int TOXIN_BINDERS = 4;
    /**
     * HOMEBREW: книга не даёт срока действия газа; взят обычный срок препаратов с. 122 — 1D6+1 минута.
     */
    private static final String DURATION = "1d6+1";

    private record Gas(String name, String stat, int failed, int saved) {
    }

    /** Галлюциноген −4 INT, тошнота −4 REF, слезоточивый −2 REF; при спасброске — половина. Снотворный — сон, при спасброске −2 ко всем */
    private static final Map<String, Gas> GASES = Map.of(
            HALLUCINOGEN, new Gas("Галлюциноген", Cp2020StatsStrategy.INT, -4, -2),
            NAUSEA, new Gas("Тошнота", Cp2020StatsStrategy.REF, -4, -2),
            TEARGAS, new Gas("Слезоточивый газ", Cp2020StatsStrategy.REF, -2, -1),
            SLEEP, new Gas("Снотворный газ", TimedEffects.ALL_STATS, 0, -2));

    private Cp2020Gas() {
    }

    static boolean isGas(String effect) {
        return effect != null && GASES.containsKey(effect);
    }

    /**
     * Спасбросок цели и эффект на её листе.
     *
     * @return строка итога для расчёта атаки
     */
    static String apply(Character target, String effect, DiceRoller dice) {
        Gas gas = GASES.get(effect);
        int body = target.getCounters().stream()
                .filter(counter -> Cp2020StatsStrategy.TYPE.equals(counter.getType()))
                .findFirst()
                .map(Cp2020StatsStrategy::body)
                .orElse(Cp2020StatsStrategy.DEFAULT_STAT);
        boolean binders = Cp2020Cyber.installed(target).anyMatch(item -> item.getName() != null && item.getName().startsWith("Toxin Binders"));
        int needed = body + (binders ? TOXIN_BINDERS : 0);
        int roll = dice.d10();
        boolean saved = roll <= needed;
        String save = target.getName() + ": спасбросок d10 = " + roll + ", нужно ≤ " + needed + (binders ? " (Toxin Binders +4)" : "");
        if (!saved && SLEEP.equals(effect)) {
            TimedEffects.add(target, "Спит: " + gas.name(), null, 0, DURATION, DurationUnit.MINUTE, "Без сознания", dice);
            return save + " — провал, спит";
        }
        int penalty = saved ? gas.saved() : gas.failed();
        TimedEffects.add(target, gas.name(), gas.stat(), penalty, DURATION, DurationUnit.MINUTE,
                saved ? "Спасбросок удался: половина эффекта" : null, dice);
        String stat = TimedEffects.ALL_STATS.equals(gas.stat()) ? "все статы" : gas.stat().toUpperCase();
        return save + (saved ? " — успех, " : " — провал, ") + stat + " " + penalty;
    }
}
