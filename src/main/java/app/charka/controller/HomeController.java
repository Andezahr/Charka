package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.service.CharacterService;
import app.charka.service.campaign.CampaignService;
import app.charka.system.GameSystemRegistry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class HomeController {

    private final CharacterService characterService;
    private final CampaignService campaignService;
    private final GameSystemRegistry systemRegistry;

    public HomeController(CharacterService characterService, CampaignService campaignService,
                          GameSystemRegistry systemRegistry) {
        this.characterService = characterService;
        this.campaignService = campaignService;
        this.systemRegistry = systemRegistry;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("characters", characterService.getAll().stream().filter(character -> !character.isNpc()).toList());
        model.addAttribute("campaigns", campaignService.getAll());
        model.addAttribute("systems", systemRegistry.getAll());
        model.addAttribute("systemNames", systemRegistry.getDisplayNames());
        return "home";
    }

    @PostMapping("/campaigns")
    public String createCampaign(@RequestParam String name,
                                 @RequestParam String system,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                 LocalDate startDate) {
        Campaign campaign = new Campaign();
        campaign.setName(name);
        campaign.setSystem(system);
        campaign.setStartDate(startDate);
        Campaign saved = campaignService.create(campaign);
        return "redirect:/campaign/" + saved.getId();
    }
}
