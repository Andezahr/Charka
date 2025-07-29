package app.charka.controller;

import app.charka.model.Campaign;

import app.charka.model.Character;
import app.charka.service.CharacterService;
import app.charka.service.campaign.CampaignService;
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

    public CampaignController(CampaignService campaignService, CharacterService characterService) {
        this.campaignService = campaignService;
        this.characterService = characterService;
    }

    @GetMapping
    public String campaignPage(@PathVariable Long campaignId, Model model) {
        Campaign campaign = campaignService.getById(campaignId);

        LocalDate currentDate = campaign.getCurrentDate();

        model.addAttribute("campaign", campaign);
        model.addAttribute("characters", campaign.getCharacters());
        model.addAttribute("currentDate", currentDate);

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