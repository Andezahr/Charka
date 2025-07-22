package app.charka.controller;

import app.charka.service.CharacterService;
import app.charka.service.campaign.CampaignService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final CharacterService characterService;
    private final CampaignService campaignService;

    public HomeController(CharacterService characterService, CampaignService campaignService) {
        this.characterService = characterService;
        this.campaignService = campaignService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("characters", characterService.getAll());
        model.addAttribute("campaigns", campaignService.getAll());
        return "home";
    }
}