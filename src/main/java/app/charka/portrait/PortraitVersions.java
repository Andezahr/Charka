package app.charka.portrait;

import app.charka.repository.CharacterPortraitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/** PH-20: у кого в кампании есть портрет и какой версии — одним запросом, без байтов картинок */
@Component
@RequiredArgsConstructor
public class PortraitVersions {

    private final CharacterPortraitRepository portraitRepository;

    /** Персонаж → версия портрета (мс); у кого портрета нет, того в карте нет */
    @Transactional(readOnly = true)
    public Map<Long, Long> of(Long campaignId) {
        Map<Long, Long> versions = new HashMap<>();
        portraitRepository.versions(campaignId).forEach(v ->
                versions.put(v.getCharacterId(), v.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()));
        return versions;
    }

    /** Адрес портрета с версией (кэшируется надолго); null — портрета нет */
    public static String url(Long campaignId, Long characterId, Map<Long, Long> versions) {
        Long version = versions.get(characterId);
        return version == null ? null : "/campaign/" + campaignId + "/portraits/" + characterId + "?v=" + version;
    }
}
