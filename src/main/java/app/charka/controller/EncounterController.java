package app.charka.controller;

import app.charka.attack.AttackResult;
import app.charka.dice.DiceService;
import app.charka.encounter.EncounterService;
import app.charka.encounter.EncounterView;
import app.charka.model.Campaign;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.service.campaign.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Боевой трекер кампании. Итог действия показывается в строке участника.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/encounter")
public class EncounterController {

    private static final int JOURNAL_SIZE = 12;

    private final EncounterService encounterService;
    private final CampaignService campaignService;
    private final DiceService diceService;

    @GetMapping
    public String page(@PathVariable Long campaignId, Model model) {
        Campaign campaign = campaignService.getById(campaignId);
        model.addAttribute("campaign", campaign);
        // куда вернуть атаку и действия с оружием
        model.addAttribute("returnCampaign", campaignId);
        Optional<EncounterView> view = encounterService.view(campaignId);
        if (view.isPresent()) {
            model.addAttribute("view", view.get());
            model.addAttribute("recentRolls", diceService.recentForCampaign(campaignId, JOURNAL_SIZE));
        } else {
            model.addAttribute("candidates", encounterService.candidates(campaignId));
        }
        return "encounter";
    }

    @PostMapping("/start")
    public String start(@PathVariable Long campaignId,
                        @RequestParam(required = false) List<Long> character,
                        RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> encounterService.start(campaignId, character == null ? List.of() : character));
    }

    @PostMapping("/next")
    public String next(@PathVariable Long campaignId, RedirectAttributes redirectAttributes) {
        try {
            return redirect(campaignId, encounterService.next(campaignId));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("encounterError", e.getMessage());
            return redirect(campaignId, null);
        }
    }

    @PostMapping("/end")
    public String end(@PathVariable Long campaignId) {
        encounterService.end(campaignId);
        return "redirect:/campaign/" + campaignId;
    }

    @PostMapping("/participants")
    public String add(@PathVariable Long campaignId, @RequestParam Long character, RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.add(campaignId, character);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/remove")
    public String remove(@PathVariable Long campaignId, @PathVariable Long participantId, RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.remove(campaignId, participantId);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/wait")
    public String waitTurn(@PathVariable Long campaignId, @PathVariable Long participantId, RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.waitTurn(campaignId, participantId);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/join")
    public String join(@PathVariable Long campaignId, @PathVariable Long participantId, RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.join(campaignId, participantId);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/initiative")
    public String initiative(@PathVariable Long campaignId, @PathVariable Long participantId, @RequestParam int initiative,
                             RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.setInitiative(campaignId, participantId, initiative);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/damage")
    public String damage(@PathVariable Long campaignId, @PathVariable Long participantId,
                         @RequestParam int amount,
                         @RequestParam(required = false) String location,
                         @RequestParam(required = false) String damageType,
                         RedirectAttributes redirectAttributes) {
        return health(campaignId, participantId, "Урон", redirectAttributes,
                () -> encounterService.damage(campaignId, participantId, amount, location, damageType));
    }

    @PostMapping("/participants/{participantId}/save/{code}")
    public String save(@PathVariable Long campaignId, @PathVariable Long participantId, @PathVariable String code,
                       RedirectAttributes redirectAttributes) {
        return health(campaignId, participantId, "Спасбросок", redirectAttributes, () -> encounterService.save(campaignId, participantId, code));
    }

    @PostMapping("/participants/{participantId}/stabilize")
    public String stabilize(@PathVariable Long campaignId, @PathVariable Long participantId, RedirectAttributes redirectAttributes) {
        return health(campaignId, participantId, "Стабилизация", redirectAttributes, () -> encounterService.stabilize(campaignId, participantId));
    }

    private String health(Long campaignId, Long participantId, String title, RedirectAttributes redirectAttributes, Supplier<HealthEvent> action) {
        Long character = encounterService.characterOf(campaignId, participantId);
        redirectAttributes.addFlashAttribute("resultCharacterId", character);
        try {
            HealthEvent event = action.get();
            redirectAttributes.addFlashAttribute("combatResult", new AttackResult(title, event.getSummary(), true, event.getSteps()));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("encounterError", e.getMessage());
        }
        return redirect(campaignId, character);
    }

    private String attempt(Long campaignId, RedirectAttributes redirectAttributes, Supplier<Object> action) {
        try {
            action.get();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("encounterError", e.getMessage());
        }
        return redirect(campaignId, null);
    }

    private static String redirect(Long campaignId, Long character) {
        return "redirect:/campaign/" + campaignId + "/encounter" + (character == null ? "" : "#combatant-" + character);
    }
}
