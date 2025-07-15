package app.charka.controller;


import app.charka.repository.CampaignRepository;
import app.charka.repository.CharacterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Autowired
    private CharacterRepository characterRepository;
    @Autowired
    private CampaignRepository campaignRepository;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("characters", characterRepository.findAll());
        model.addAttribute("campaigns", campaignRepository.findAll());
        return "home";
    }
}