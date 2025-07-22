package app.charka.controller;

import app.charka.Routes;
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

    @PostMapping(Routes.CHRONICLE_ADD)
    public String addChronicle(
            @PathVariable Long campaignId,
            @RequestParam String name,
            @RequestParam Integer duration
    ) {
        campaignTimelineService.proceedDate(campaignId, duration, name);
        return Routes.CAMPAIGN_REDIRECT + campaignId;
    }
}
