package app.charka.controller;

import app.charka.attack.AttackRequest;
import app.charka.attack.AttackResult;
import app.charka.attack.AttackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                         @RequestParam(required = false) List<Long> target,
                         @RequestParam(required = false) String targetName,
                         @RequestParam(required = false) Integer distance,
                         @RequestParam(required = false) String location,
                         @RequestParam(required = false) String skill,
                         @RequestParam(defaultValue = "0") int modifier,
                         @RequestParam(required = false) String mode,
                         @RequestParam(defaultValue = "0") int rounds,
                         @RequestParam(defaultValue = "0") int zoneWidth,
                         @RequestParam(required = false) Long returnCampaign,
                         @RequestParam Map<String, String> params,
                         RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("actionItemId", itemId);
        String anchor = "#item-" + itemId;
        try {
            AttackResult result = attackService.attack(characterId, itemId,
                    new AttackRequest(targets(target), targetName, distance, location, skill, options(params), modifier, mode, rounds, zoneWidth));
            redirectAttributes.addFlashAttribute("attackResult", result);
            if (result.detached()) {
                anchor = "#inventories";
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("actionError", e.getMessage());
        }
        return back(characterId, returnCampaign, anchor);
    }

    /**
     * Заряд упал мимо цели: мастер выбирает, кого задело. Урон брошен при атаке и хранится на сервере.
     */
    @PostMapping("/character/{characterId}/attack/blast/{blastId}")
    public String applyBlast(@PathVariable Long characterId,
                             @PathVariable Long blastId,
                             @RequestParam(required = false) List<Long> target,
                             @RequestParam(required = false) Long returnCampaign,
                             RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("attackResult", attackService.applyBlast(characterId, blastId, targets(target)));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("blastError", e.getMessage());
        }
        return back(characterId, returnCampaign, "#inventories");
    }

    @PostMapping("/character/{characterId}/attack/blast/{blastId}/dismiss")
    public String dismissBlast(@PathVariable Long characterId, @PathVariable Long blastId,
                               @RequestParam(required = false) Long returnCampaign) {
        attackService.dismissBlast(characterId, blastId);
        return back(characterId, returnCampaign, "#inventories");
    }

    /** Атака из боевого трекера возвращает в строку участника, с листа — к оружию */
    private static String back(Long characterId, Long returnCampaign, String anchor) {
        return returnCampaign != null
                ? "redirect:/campaign/" + returnCampaign + "/encounter#combatant-" + characterId
                : "redirect:/character/" + characterId + anchor;
    }

    /** Пустой пункт «Без листа» приходит как null */
    private static List<Long> targets(List<Long> target) {
        return target == null ? List.of() : target.stream().filter(Objects::nonNull).toList();
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
