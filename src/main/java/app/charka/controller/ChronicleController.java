package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Campaign;
import app.charka.repository.CampaignRepository;
import app.charka.service.CampaignTimelineService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
public class ChronicleController {
    private final CampaignRepository campaignRepository;
    private final CampaignTimelineService campaignTimelineService;

    public ChronicleController(CampaignRepository campaignRepository, CampaignTimelineService campaignTimelineService) {
        this.campaignRepository = campaignRepository;
        this.campaignTimelineService = campaignTimelineService;
    }

    @PostMapping(Routes.CHRONICLE_ADD)
    public String addChronicle(
            @PathVariable Long campaignId,
            @RequestParam String name,
            @RequestParam Integer duration
    ) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Кампания не найдена: " + campaignId));

        campaignTimelineService.proceedDate(campaign, duration, name);

        return Routes.CAMPAIGN_REDIRECT + campaignId;
    }
}
