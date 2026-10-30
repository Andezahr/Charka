package app.charka.controller;

import app.charka.dice.DiceService;
import app.charka.model.Campaign;

import app.charka.model.Character;
import app.charka.service.CharacterService;
import app.charka.service.campaign.CampaignService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/campaign/{campaignId}")
public class CampaignController {

    private final CampaignService campaignService;
    private final CharacterService characterService;
    private final GameSystemRegistry systemRegistry;
    private final DiceService diceService;

    public CampaignController(CampaignService campaignService, CharacterService characterService,
                              GameSystemRegistry systemRegistry, DiceService diceService) {
        this.campaignService = campaignService;
        this.characterService = characterService;
        this.systemRegistry = systemRegistry;
        this.diceService = diceService;
    }

    @GetMapping
    public String campaignPage(@PathVariable Long campaignId, Model model) {
        Campaign campaign = campaignService.getById(campaignId);

        LocalDate currentDate = campaign.getCurrentDate();

        model.addAttribute("campaign", campaign);
        model.addAttribute("players", campaign.getCharacters().stream().filter(character -> !character.isNpc()).toList());
        model.addAttribute("npcs", campaign.getCharacters().stream().filter(Character::isNpc).toList());
        model.addAttribute("currentDate", currentDate);
        GameSystem gameSystem = systemRegistry.find(campaign.getSystem()).orElseGet(systemRegistry::getGeneric);
        model.addAttribute("gameSystem", gameSystem);
        model.addAttribute("npcTemplates", gameSystem.getNpcTemplates());
        model.addAttribute("briefs", campaign.getCharacters().stream()
                .collect(Collectors.toMap(Character::getId, gameSystem::getCharacterBrief)));
        model.addAttribute("quickDice", gameSystem.getQuickDice());
        model.addAttribute("difficultyPresets", gameSystem.getCheckResolver().getDifficultyPresets());
        model.addAttribute("recentRolls", diceService.recentForCampaign(campaignId, 20));

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