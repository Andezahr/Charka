package app.charka.controller;

import app.charka.service.campaign.CampaignTimelineService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
public class ChronicleController {
    private final CampaignTimelineService campaignTimelineService;

    public ChronicleController(CampaignTimelineService campaignTimelineService) {
        this.campaignTimelineService = campaignTimelineService;
    }

    @PostMapping("/campaign/{campaignId}/chronicles")
    public String addChronicle(
            @PathVariable Long campaignId,
            @RequestParam String name,
            @RequestParam Integer duration,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes
    ) {
        redirectAttributes.addFlashAttribute("timeReport", campaignTimelineService.proceedDate(campaignId, duration, name));
        return "redirect:/campaign/" + campaignId + "#session";
    }

    /** Новая сессия: Удача и прочее, что возвращается к началу сессии */
    @PostMapping("/campaign/{campaignId}/session")
    public String newSession(@PathVariable Long campaignId,
                             org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        campaignTimelineService.newSession(campaignId);
        redirectAttributes.addFlashAttribute("timeReport", java.util.List.of("Новая сессия: Удача вернулась"));
        return "redirect:/campaign/" + campaignId + "#session";
    }
}
