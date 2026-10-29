package app.charka.controller;

import app.charka.model.Campaign;

import app.charka.model.Character;
import app.charka.service.CharacterService;
import app.charka.service.campaign.CampaignService;
import app.charka.system.GameSystemRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/campaign/{campaignId}")
public class CampaignController {

    private final CampaignService campaignService;
    private final CharacterService characterService;
    private final GameSystemRegistry systemRegistry;

    public CampaignController(CampaignService campaignService, CharacterService characterService,
                              GameSystemRegistry systemRegistry) {
        this.campaignService = campaignService;
        this.characterService = characterService;
        this.systemRegistry = systemRegistry;
    }

    @GetMapping
    public String campaignPage(@PathVariable Long campaignId, Model model) {
        Campaign campaign = campaignService.getById(campaignId);

        LocalDate currentDate = campaign.getCurrentDate();

        model.addAttribute("campaign", campaign);
        model.addAttribute("characters", campaign.getCharacters());
        model.addAttribute("currentDate", currentDate);
        model.addAttribute("gameSystem", systemRegistry.find(campaign.getSystem())
                .orElseGet(systemRegistry::getGeneric));

        return "campaign";
    }

    @PostMapping("/characters")
    public String addCharacterToCampaign(@PathVariable Long campaignId, @RequestParam String name) {
        Character newCharacter = new Character();
        newCharacter.setName(name);
        characterService.createInCampaign(campaignId, newCharacter);
        return "redirect:/campaign/" + campaignId;
    }
}