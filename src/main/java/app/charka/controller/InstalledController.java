package app.charka.controller;

import app.charka.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Действие блока вживлённого на листе, а не отдельного предмета: терапия. Итог показывается в самом блоке.
 */
@Controller
@RequiredArgsConstructor
public class InstalledController {

    private final ItemService itemService;

    @PostMapping("/character/{characterId}/installed/{action}")
    public String perform(@PathVariable Long characterId, @PathVariable String action,
                          @RequestParam Map<String, String> params, RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("installedResult", itemService.performInstalledAction(characterId, action, params));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("installedError", e.getMessage());
        }
        return "redirect:/character/" + characterId + "#installed";
    }
}
