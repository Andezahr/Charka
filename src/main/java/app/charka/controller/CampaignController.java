package app.charka.controller;

import app.charka.model.Campaign;

import app.charka.service.campaign.CampaignService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/campaign")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping("/{campaignId}")
    public String campaignPage(@PathVariable Long campaignId, Model model) {
        Campaign campaign = campaignService.getById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Кампания не найдена: " + campaignId));

        LocalDate currentDate = campaign.getCurrentDate();

        model.addAttribute("campaign", campaign);
        model.addAttribute("characters", campaign.getCharacters());
        model.addAttribute("currentDate", currentDate);

        return "campaign";
    }
}