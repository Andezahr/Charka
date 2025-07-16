package app.charka.controller;


import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final CharacterRepository characterRepository;
    private final CampaignRepository campaignRepository;

    public HomeController(CharacterRepository characterRepository, CampaignRepository campaignRepository) {
        this.characterRepository = characterRepository;
        this.campaignRepository = campaignRepository;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("characters", characterRepository.findAll());
        model.addAttribute("campaigns", campaignRepository.findAll());
        return "home";
    }
}