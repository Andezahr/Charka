package app.charka.controller;

import app.charka.undo.UndoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Отмена последнего действия (A-01): кнопка «↶ Отменить» под итогом действия и в ленте чата.
 * Возвращает туда, откуда нажали.
 */
@Controller
@RequiredArgsConstructor
public class UndoController {

    private final UndoService undoService;
    private final app.charka.undo.RerollService rerollService;

    /**
     * K-09: перебросить за вдохновение — отменить действие, потратить вдохновение и выполнить тот же запрос ещё раз.
     * Поля исходного запроса приходят из плашки скрытыми полями.
     */
    @PostMapping("/character/{characterId}/reroll")
    public String reroll(@PathVariable Long characterId,
                         @org.springframework.web.bind.annotation.RequestParam(required = false) Long rerollEntry,
                         @org.springframework.web.bind.annotation.RequestParam String rerollPath,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            return "forward:" + rerollService.prepare(characterId, rerollEntry, rerollPath);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("undoError", e.getMessage());
            return "redirect:" + Referers.back(request, "/character/" + characterId);
        }
    }

    @PostMapping("/campaign/{campaignId}/undo/{entryId}")
    public String undo(@PathVariable Long campaignId, @PathVariable Long entryId, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("undoDone", "Отменено: " + undoService.undo(campaignId, entryId));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("undoError", e.getMessage());
        }
        return "redirect:" + Referers.back(request, "/campaign/" + campaignId);
    }
}
