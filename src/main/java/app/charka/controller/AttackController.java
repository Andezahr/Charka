package app.charka.controller;

import app.charka.attack.AttackRequest;
import app.charka.attack.AttackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Атака оружием с листа персонажа. Итог показывается в строке оружия и остаётся там до следующего действия.
 */
@Controller
@RequiredArgsConstructor
public class AttackController {

    private static final String OPTION_PREFIX = "option.";

    private final AttackService attackService;

    /**
     * Модификаторы приходят параметрами {@code option.<выбор>} со значением — кодом; пустое значение — ничего не выбрано.
     */
    @PostMapping("/character/{characterId}/items/{itemId}/attack")
    public String attack(@PathVariable Long characterId,
                         @PathVariable Long itemId,
                         @RequestParam(required = false) Long target,
                         @RequestParam(required = false) String targetName,
                         @RequestParam(required = false) Integer distance,
                         @RequestParam(required = false) String location,
                         @RequestParam(required = false) String skill,
                         @RequestParam(defaultValue = "0") int modifier,
                         @RequestParam Map<String, String> params,
                         RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("actionItemId", itemId);
        try {
            if (distance == null) {
                throw new IllegalArgumentException("Укажите расстояние до цели");
            }
            redirectAttributes.addFlashAttribute("attackResult", attackService.attack(characterId, itemId,
                    new AttackRequest(target, targetName, distance, location, skill, options(params), modifier)));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("actionError", e.getMessage());
        }
        return "redirect:/character/" + characterId + "#item-" + itemId;
    }

    private static Set<String> options(Map<String, String> params) {
        Set<String> options = new HashSet<>();
        params.forEach((key, value) -> {
            if (key.startsWith(OPTION_PREFIX) && !value.isBlank()) {
                options.add(value);
            }
        });
        return options;
    }
}
