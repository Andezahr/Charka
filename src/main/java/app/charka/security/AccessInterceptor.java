package app.charka.security;

import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Права по адресу (U-03): чужое — 404, как будто его нет. Одна проверка на все маршруты, контроллеры о ней не знают.
 * Игроку в кампании доступны её страница, бой (кроме начала, конца и смены карты), карта боя и чат; остальное — мастеру.
 */
@Component
@RequiredArgsConstructor
public class AccessInterceptor implements HandlerInterceptor {

    private static final Pattern CAMPAIGN = Pattern.compile("^/campaign/(\\d+)(/.*)?$");
    private static final Pattern CHARACTER = Pattern.compile("^/(?:api/)?characters?/(\\d+)(/.*)?$");
    private static final Pattern EXPORT = Pattern.compile("^/api/campaigns/(\\d+)(/.*)?$");

    /** Бой — для всех за столом, но начать, закончить и править карту может только мастер */
    private static final Set<String> GM_ONLY_IN_FIGHT = Set.of("/encounter/start", "/encounter/end", "/encounter/attach",
            "/encounter/save-map", "/encounter/edit-map", "/encounter/map/doc", "/encounter/participants");

    private final AccessService accessService;
    private final CampaignRepository campaignRepository;
    private final CharacterRepository characterRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!accessService.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Matcher campaign = CAMPAIGN.matcher(path);
        if (campaign.matches()) {
            checkCampaign(Long.valueOf(campaign.group(1)), campaign.group(2) == null ? "" : campaign.group(2), request.getMethod());
            return true;
        }
        Matcher character = CHARACTER.matcher(path);
        if (character.matches()) {
            Character found = characterRepository.findById(Long.valueOf(character.group(1)))
                    .orElseThrow(NotFoundException.forId("Character", Long.valueOf(character.group(1))));
            boolean npcTool = character.group(2) != null && character.group(2).startsWith("/npc/");
            if (!accessService.canControl(found) || (npcTool && !accessService.isGm(found.getCampaign()))) {
                throw NotFoundException.forId("Character", found.getId()).get();
            }
            return true;
        }
        Matcher export = EXPORT.matcher(path);
        if (export.matches()) {
            checkCampaign(Long.valueOf(export.group(1)), "/export", request.getMethod());
        }
        return true;
    }

    private void checkCampaign(Long id, String rest, String method) {
        Campaign campaign = campaignRepository.findById(id).orElseThrow(NotFoundException.forId("Campaign", id));
        if (accessService.isGm(campaign)) {
            return;
        }
        boolean playerPath = (rest.isEmpty() && "GET".equals(method))
                || rest.equals("/chat")
                || ((rest.equals("/encounter") || rest.startsWith("/encounter/")) && !GM_ONLY_IN_FIGHT.contains(rest));
        if (!playerPath || !accessService.isPlayer(campaign)) {
            throw NotFoundException.forId("Campaign", id).get();
        }
    }
}
