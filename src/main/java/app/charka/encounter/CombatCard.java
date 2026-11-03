package app.charka.encounter;

import java.util.List;

/**
 * Что система показывает об участнике боя в строке трекера.
 *
 * @param healthCounterId счётчик здоровья; null — урон применяет мастер, формы урона нет
 * @param status          состояние словами; null — система его не знает
 * @param out             выбыл: ход пропускается, целью не предлагается
 * @param alarming        состояние требует внимания
 * @param lines           короткие значения: броня, штрафы
 * @param saves           спасброски, которые нужны сейчас
 * @param stabilizable    можно отметить, что медик стабилизировал
 * @param locations       зоны для формы урона
 * @param damageTypes     типы урона для формы урона
 */
public record CombatCard(Long healthCounterId, String status, boolean out, boolean alarming, List<String> lines,
                         List<Save> saves, boolean stabilizable, List<Option> locations, List<Option> damageTypes) {

    public record Save(String code, String label) {
    }

    public record Option(String code, String label) {
    }

    /** Система не знает о здоровье: только короткие значения */
    public static CombatCard brief(List<String> lines) {
        return new CombatCard(null, null, false, false, lines, List.of(), false, List.of(), List.of());
    }
}
