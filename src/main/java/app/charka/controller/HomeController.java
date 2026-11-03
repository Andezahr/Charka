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
    private final app.charka.service.campaign.CampaignImportService importService;
    private final app.charka.security.AccessService accessService;

    public HomeController(CharacterService characterService, CampaignService campaignService,
                          GameSystemRegistry systemRegistry, app.charka.service.campaign.CampaignImportService importService,
                          app.charka.security.AccessService accessService) {
        this.characterService = characterService;
        this.campaignService = campaignService;
        this.systemRegistry = systemRegistry;
        this.importService = importService;
        this.accessService = accessService;
    }

    @GetMapping("/")
    public String home(Model model) {
        // U-03: только свои кампании (мастер или игрок) и свои персонажи
        model.addAttribute("characters", characterService.getAll().stream()
                .filter(character -> !character.isNpc() && accessService.canControl(character)).toList());
        model.addAttribute("campaigns", campaignService.getAll().stream().filter(accessService::canOpen).toList());
        model.addAttribute("systems", systemRegistry.getAll());
        model.addAttribute("systemNames", systemRegistry.getDisplayNames());
        return "home";
    }

    /** Импорт кампании из JSON экспорта: новая кампания, файл не трогает существующие */
    @PostMapping("/campaigns/import")
    public String importCampaign(@org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                 org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            app.charka.model.Campaign imported = importService.importCampaign(new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8));
            return "redirect:/campaign/" + imported.getId();
        } catch (IllegalArgumentException | java.io.IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/";
        }
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
