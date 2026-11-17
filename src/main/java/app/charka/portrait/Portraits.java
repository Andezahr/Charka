package app.charka.portrait;

import app.charka.model.Character;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;

/**
 * PH-20: адреса портретов для шаблонов — {@code @portraits.url(character)}. Версии кампании читаются
 * один раз за запрос, сколько бы строк ни рисовалось.
 */
@Component("portraits")
@RequestScope
@RequiredArgsConstructor
public class Portraits {

    private final PortraitVersions portraitVersions;
    private final Map<Long, Map<Long, Long>> byCampaign = new HashMap<>();

    /** null — портрета нет, рисуется буква */
    public String url(Character character) {
        if (character == null || character.getId() == null || character.getCampaign() == null) {
            return null;
        }
        Long campaignId = character.getCampaign().getId();
        return PortraitVersions.url(campaignId, character.getId(), byCampaign.computeIfAbsent(campaignId, portraitVersions::of));
    }

    /** Адрес по номеру персонажа кампании (лента чата); null — портрета нет */
    public String url(Long campaignId, Long characterId) {
        if (campaignId == null || characterId == null) {
            return null;
        }
        return PortraitVersions.url(campaignId, characterId, byCampaign.computeIfAbsent(campaignId, portraitVersions::of));
    }

    /** Первая буква имени — пока портрета нет */
    public static String letter(String name) {
        return name == null || name.isBlank() ? "?" : name.strip().substring(0, 1).toUpperCase();
    }
}
