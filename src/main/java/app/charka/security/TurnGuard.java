package app.charka.security;

import app.charka.encounter.EncounterService;
import app.charka.model.Character;
import app.charka.model.Encounter;
import app.charka.model.EncounterParticipant;
import app.charka.repository.CharacterRepository;
import app.charka.repository.EncounterRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TURN-01: в бою игрок вне своего хода ничего не меняет — запрещено всё, кроме явного списка ниже.
 * <p>
 * Проверка — на изменяющие запросы (не GET) вошедшего игрока: к персонажу ({@code /character/{id}/…}), который сейчас
 * в бою своей кампании и не ходит, и к бою кампании ({@code /campaign/{id}/encounter/…}) — от имени своего участника.
 * Мастер (оба режима карты) — без ограничений; персонаж не в бою — без проверки. Новый маршрут закрыт сам, пока его не
 * внесли в список. {@code RoundActions.requireTurn} в сервисах остаётся вторым рубежом.
 */
@Component
@RequiredArgsConstructor
public class TurnGuard implements HandlerInterceptor {

    private static final Pattern CHARACTER = Pattern.compile("^/(?:api/)?characters?/(\\d+)(/.*)?$");
    private static final Pattern ENCOUNTER = Pattern.compile("^/campaign/(\\d+)(/encounter(?:/.*)?)$");
    private static final Pattern PARTICIPANT = Pattern.compile("^/encounter(?:/map)?/participants/(\\d+)(?:/.*)?$");
    private static final Pattern TRAY_MOVE = Pattern.compile("^/encounter/map/participants/(\\d+)/move$");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /** Можно вне хода с листа: урон и спасброски приходят на чужом ходу; записи игрока, не меняющие боя */
    static final List<Pattern> CHARACTER_ALLOWED = List.of(
            Pattern.compile("^/counters/\\d+/health/(damage|save/[^/]+)$"),
            Pattern.compile("^/notes(/.*)?$"),
            Pattern.compile("^/categories(/.*)?$"),
            Pattern.compile("^/contacts(/.*)?$"),
            Pattern.compile("^/profile$"),
            Pattern.compile("^/skills/pin$"),
            Pattern.compile("^/portrait(/delete)?$"));

    /**
     * Можно вне хода в бою: получить урон и спасбросок, объявить до инициативы (CP2020 Fast Draw), вступить из ожидания,
     * указатель на карте; «Закончить ход» отказывает сам с понятной подписью.
     */
    static final List<Pattern> ENCOUNTER_ALLOWED = List.of(
            Pattern.compile("^/encounter/participants/\\d+/(damage|save/[^/]+|declare/[^/]+|join)$"),
            Pattern.compile("^/encounter/map/pointer$"),
            Pattern.compile("^/encounter/next$"));

    private final AccessService accessService;
    private final CharacterRepository characterRepository;
    private final EncounterRepository encounterRepository;
    private final EncounterService encounterService;

    @Override
    @Transactional(readOnly = true)
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (SAFE_METHODS.contains(request.getMethod()) || !accessService.isEnabled() || accessService.current().isEmpty()) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Matcher character = CHARACTER.matcher(path);
        if (character.matches()) {
            checkCharacter(Long.valueOf(character.group(1)), character.group(2) == null ? "" : character.group(2));
            return true;
        }
        Matcher encounter = ENCOUNTER.matcher(path);
        if (encounter.matches()) {
            checkEncounter(Long.valueOf(encounter.group(1)), encounter.group(2));
        }
        return true;
    }

    private void checkCharacter(Long characterId, String rest) {
        if (allowed(CHARACTER_ALLOWED, rest)) {
            return;
        }
        Optional<Character> found = characterRepository.findById(characterId);
        if (found.isPresent() && waitsForTurn(found.get())) {
            throw new app.charka.exception.NotYourTurnException();
        }
    }

    /**
     * Персонаж в идущем бою своей кампании, ходит не он, а смотрит не мастер — лист только для просмотра
     * (кроме разрешённого списка). Для подписи на листе и для отказа.
     */
    @Transactional(readOnly = true)
    public boolean waitsForTurn(Character character) {
        if (!accessService.isEnabled() || accessService.current().isEmpty() || character.getCampaign() == null
                || accessService.isGm(character.getCampaign())) {
            return false;
        }
        Long campaignId = character.getCampaign().getId();
        boolean inFight = encounterRepository.findByCampaignId(campaignId).stream().flatMap(e -> e.getParticipants().stream())
                .anyMatch(participant -> participant.getCharacter().getId().equals(character.getId()));
        return inFight && !character.getId().equals(encounterService.currentCharacterId(campaignId));
    }

    private void checkEncounter(Long campaignId, String rest) {
        Optional<Encounter> encounter = encounterRepository.findByCampaignId(campaignId);
        if (encounter.isEmpty() || accessService.isGm(encounter.get().getCampaign()) || allowed(ENCOUNTER_ALLOWED, rest)) {
            return;
        }
        List<EncounterParticipant> acting;
        Matcher participant = PARTICIPANT.matcher(rest);
        if (participant.matches()) {
            Long participantId = Long.valueOf(participant.group(1));
            acting = encounter.get().getParticipants().stream().filter(p -> p.getId().equals(participantId)).toList();
            // BF-02: фишку из лотка игрок ставит когда угодно
            if (TRAY_MOVE.matcher(rest).matches() && acting.stream().allMatch(p -> p.getPosX() == null)) {
                return;
            }
        } else {
            acting = encounter.get().getParticipants().stream()
                    .filter(p -> accessService.canControl(p.getCharacter())).toList();
        }
        if (acting.isEmpty()) {
            // не участник боя — права решает AccessInterceptor
            return;
        }
        Long current = encounterService.currentCharacterId(campaignId);
        if (acting.stream().noneMatch(p -> Objects.equals(p.getCharacter().getId(), current))) {
            throw new app.charka.exception.NotYourTurnException();
        }
    }

    private static boolean allowed(List<Pattern> patterns, String rest) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(rest).matches());
    }
}
