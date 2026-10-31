package app.charka.system.cp2020;

import app.charka.attack.AttackHit;
import app.charka.attack.LingeringEffect;
import app.charka.item.ItemField.Option;

import java.util.List;
import java.util.Map;

/**
 * Что особый заряд делает с целью сверх урона: виды гранат (с. 63), огнемёт (с. 110).
 * Бросок первого раунда — урон самого заряда, здесь только следующие раунды.
 */
final class Cp2020Effects {

    static final String INCENDIARY = "INCENDIARY";
    static final String FLAME = "FLAME";
    static final String STUN = "STUN";
    static final String DAZZLE = "DAZZLE";
    static final String SONIC = "SONIC";
    static final String GAS = "GAS";

    static final List<Option> OPTIONS = List.of(
            new Option(INCENDIARY, "Зажигательный: 4D6 ещё 2 раунда"),
            new Option(FLAME, "Огнемёт: 1D10, затем 1D6"),
            new Option(STUN, "Оглушающий: спасбросок −5"),
            new Option(DAZZLE, "Ослепляющий: 4 раунда"),
            new Option(SONIC, "Звуковой: глухота 4 раунда"),
            new Option(GAS, "Смертельный газ: спасбросок, два раунда"));

    /** С. 63: «−5 to Stun» */
    private static final int STUN_GRENADE_SAVE = -5;
    /** С. 63: «Blind for 4 turns», «deafened 4 turns» */
    private static final int SENSES_ROUNDS = 4;
    /** Модификатор атаки ослеплённого: {@link Cp2020AttackModifiers} */
    private static final String BLINDED_OPTION = "blinded";

    private Cp2020Effects() {
    }

    /** С огнемётом свои правила брони, от газа броня не защищает (с. 110) */
    static String damageType(String effect, String usual) {
        if (GAS.equals(effect)) {
            return Cp2020DamageType.BYPASS.name();
        }
        return FLAME.equals(effect) ? Cp2020DamageType.FLAME.name() : usual;
    }

    /** Заряд и без урона делает своё дело: мастеру применять нечего */
    static boolean isKnown(String effect) {
        return effect != null && OPTIONS.stream().anyMatch(option -> option.value().equals(effect));
    }

    static AttackHit.HitSave save(String effect) {
        return STUN.equals(effect) ? new AttackHit.HitSave(Cp2020HealthStrategy.SAVE_STUN, STUN_GRENADE_SAVE) : null;
    }

    /**
     * @param expression бросок урона самого заряда
     * @param damageData как считать урон следующих раундов
     */
    static List<LingeringEffect> lingering(String effect, String expression, Map<String, String> damageData) {
        if (effect == null) {
            return List.of();
        }
        return switch (effect) {
            // С. 63: «4D6 for 3 turns», первый — сам взрыв
            case INCENDIARY -> List.of(burn(List.of("4D6", "4D6"), damageData));
            // С. 110: «2D10 the 1st turn, 1D10 and 1D6 the following two turns»
            case FLAME -> List.of(burn(List.of("1D10", "1D6"), damageData));
            case DAZZLE -> List.of(new LingeringEffect("Ослеплён −3", SENSES_ROUNDS, List.of(), Map.of(), BLINDED_OPTION));
            // С. 110: на следующий раунд облако ещё здесь, на третий рассеивается. Кто вышел из него, с того эффект снимает мастер
            case GAS -> List.of(new LingeringEffect("В облаке газа", 1, List.of(expression), damageData, null));
            case SONIC -> List.of(new LingeringEffect("Оглох", SENSES_ROUNDS, List.of(), Map.of(), null));
            default -> List.of();
        };
    }

    private static LingeringEffect burn(List<String> rolls, Map<String, String> damageData) {
        return new LingeringEffect("Горит", rolls.size(), rolls, damageData, null);
    }
}
