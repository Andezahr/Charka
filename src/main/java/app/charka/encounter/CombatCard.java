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
 * @param segments        UX-30: здоровье по частям одной полосой (Космозэки: цифры 0…9, цвет — ХП зоны); пусто — полосы нет
 */
public record CombatCard(Long healthCounterId, String status, boolean out, boolean alarming, List<String> lines,
                         List<Save> saves, boolean stabilizable, List<Option> locations, List<Option> damageTypes,
                         List<Segment> segments) {

    public CombatCard(Long healthCounterId, String status, boolean out, boolean alarming, List<String> lines,
                      List<Save> saves, boolean stabilizable, List<Option> locations, List<Option> damageTypes) {
        this(healthCounterId, status, out, alarming, lines, saves, stabilizable, locations, damageTypes, List.of());
    }

    /**
     * Клетка полосы здоровья.
     *
     * @param label подпись в клетке (цифра кубика)
     * @param css   ok, hurt, low, down, none — цвет
     * @param title что это: зона и ХП, для подсказки и экранного диктора
     */
    public record Segment(String label, String css, String title) {
    }

    public record Save(String code, String label) {
    }

    public record Option(String code, String label) {
    }

    /** Система не знает о здоровье: только короткие значения */
    public static CombatCard brief(List<String> lines) {
        return new CombatCard(null, null, false, false, lines, List.of(), false, List.of(), List.of());
    }
}
