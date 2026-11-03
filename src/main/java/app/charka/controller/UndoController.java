package app.charka.controller;

import app.charka.undo.UndoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/**
 * Отмена последнего действия (A-01): кнопка «↶ Отменить» под итогом действия и в ленте чата.
 * Возвращает туда, откуда нажали.
 */
@Controller
@RequiredArgsConstructor
public class UndoController {

    private final UndoService undoService;

    @PostMapping("/campaign/{campaignId}/undo/{entryId}")
    public String undo(@PathVariable Long campaignId, @PathVariable Long entryId, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("undoDone", "Отменено: " + undoService.undo(campaignId, entryId));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("undoError", e.getMessage());
        }
        return "redirect:" + back(request, campaignId);
    }

    /** Страница, с которой нажали, если она этого же сайта; иначе кампания */
    private static String back(HttpServletRequest request, Long campaignId) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                String path = uri.getRawPath();
                if (path != null && path.startsWith("/") && !path.startsWith("//")
                        && (uri.getHost() == null || uri.getHost().equals(request.getServerName()))) {
                    return path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                }
            } catch (IllegalArgumentException ignored) {
                // кривой заголовок — на страницу кампании
            }
        }
        return "/campaign/" + campaignId;
    }
}
