package app.charka.undo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Отмена (A-01) для всех действий игры сразу: перед изменяющим запросом кампании или персонажа — снимок,
 * после удачного — запись и кнопка «↶ Отменить» во flash {@code undoOffer}. Контроллеры о ней не знают.
 * Не отслеживаются заметки, контакты, чат, карты, ход фишек и создание или удаление персонажей.
 */
@Component
@RequiredArgsConstructor
public class UndoInterceptor implements HandlerInterceptor {

    public static final String OFFER = "undoOffer";

    private static final Pattern CAMPAIGN = Pattern.compile("^/campaign/(\\d+)(/.*)?$");
    private static final Pattern CHARACTER = Pattern.compile("^/character/(\\d+)(/.*)?$");

    /** Части адреса, по которым действие не отменяется: не игра, или его поправить проще, чем отменить */
    private static final List<String> SKIPPED = List.of("/notes", "/categories", "/contacts", "/chat", "/maps", "/undo",
            "/roll", "/npc/", "/characters", "/player", "/encounter/map", "/encounter/attach", "/encounter/save-map",
            // A-06: заготовки — не игра; начало сцены создаёт NPC, их отмена не уберёт
            "/scenes", "/invite",
            // R-07: основное оружие — только место на листе
            "/main-weapon",
            // R-09a: закреплённые навыки — только место на листе
            "/skills/pin",
            // A-07: бросок по запросу — как бросок; запрос уже брошен, отменять его нечем
            "/requests");
    private static final Pattern SKIPPED_EXACT = Pattern.compile("^/(check|edit)$");

    private static final String BEFORE = UndoInterceptor.class.getName() + ".before";
    private static final String LABEL = UndoInterceptor.class.getName() + ".label";
    private static final String ACTOR = UndoInterceptor.class.getName() + ".actor";

    private final UndoService undoService;
    private final UndoLabels labels;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"DELETE".equals(method)) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Long campaignId;
        Long actor = null;
        String rest;
        Matcher campaign = CAMPAIGN.matcher(path);
        Matcher character = CHARACTER.matcher(path);
        if (campaign.matches()) {
            campaignId = Long.valueOf(campaign.group(1));
            rest = campaign.group(2) == null ? "" : campaign.group(2);
        } else if (character.matches()) {
            actor = Long.valueOf(character.group(1));
            campaignId = undoService.campaignOf(actor);
            rest = character.group(2) == null ? "" : character.group(2);
        } else {
            return true;
        }
        if (campaignId == null || rest.isEmpty() || SKIPPED_EXACT.matcher(rest).matches()
                || SKIPPED.stream().anyMatch(rest::contains)) {
            return true;
        }
        request.setAttribute(BEFORE, undoService.snapshot(campaignId));
        request.setAttribute(LABEL, labels.label(rest, actor, "true".equals(request.getParameter("buy")),
                request.getParameter("catalogKey"), request.getParameter("itemName")));
        request.setAttribute(ACTOR, actor);
        return true;
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler,
                           ModelAndView modelAndView) {
        if (!(request.getAttribute(BEFORE) instanceof CampaignSnapshots.Snapshot before)) {
            return;
        }
        undoService.record(before, (String) request.getAttribute(LABEL), (Long) request.getAttribute(ACTOR))
                .ifPresent(offer -> RequestContextUtils.getOutputFlashMap(request).put(OFFER, offer));
    }
}
