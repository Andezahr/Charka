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
