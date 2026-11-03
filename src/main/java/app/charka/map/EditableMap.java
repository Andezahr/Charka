package app.charka.map;

import java.util.List;
import java.util.Map;

/**
 * Карта для редактора: документ и правила системы, по которым браузер рисует инструменты.
 *
 * @param id       карта кампании; у копии боя — с какой карты она взята
 * @param defaults      материал по умолчанию у инструмента ({@link MapRules#defaults})
 * @param backgroundUrl адрес подложки; null — её нет
 */
public record EditableMap(Long id, String name, MapDoc doc, String metric, List<Material> materials, List<MapObject> objects,
                          Map<String, String> defaults, String backgroundUrl) {

    public static EditableMap of(Long campaignId, Long id, String name, MapDoc doc, MapRules rules) {
        return new EditableMap(id, name, doc, rules.metric(), rules.materials(), rules.objects(), rules.defaults(),
                backgroundUrl(campaignId, id, doc));
    }

    /** Адрес подложки с версией; карта-источник удалена — картинки нет, адрес даст 404 */
    public static String backgroundUrl(Long campaignId, Long mapId, MapDoc doc) {
        return doc.bg() == null || mapId == null ? null
                : "/campaign/" + campaignId + "/maps/" + mapId + "/background?v=" + doc.bg().ver();
    }
}
