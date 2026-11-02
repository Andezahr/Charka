package app.charka.controller;

import app.charka.creation.CharacterCreation;
import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.service.campaign.CampaignService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Мастер создания персонажа: страница системы ({@link CharacterCreation}). У системы без мастера — назад к кампании.
 */
@Controller
@RequestMapping("/campaign/{campaignId}/characters/new")
public class CharacterCreationController {

    private final CampaignService campaignService;
    private final List<CharacterCreation> creations;

    public CharacterCreationController(CampaignService campaignService, List<CharacterCreation> creations) {
        this.campaignService = campaignService;
        this.creations = creations;
    }

    @GetMapping
    public String page(@PathVariable Long campaignId, Model model) {
        Campaign campaign = campaignService.getById(campaignId);
        Optional<CharacterCreation> creation = creationFor(campaign);
        if (creation.isEmpty()) {
            return "redirect:/campaign/" + campaignId;
        }
        model.addAttribute("campaign", campaign);
        model.addAttribute("creation", creation.get().form(campaign));
        return creation.get().getTemplate();
    }

    /** Бросок очков: ответ странице, без перезагрузки */
    @PostMapping("/roll")
    @ResponseBody
    public CharacterCreation.Roll roll(@PathVariable Long campaignId, @RequestParam String method) {
        Campaign campaign = campaignService.getById(campaignId);
        return creationFor(campaign).orElseThrow(() -> new IllegalArgumentException("В этой системе нет мастера создания"))
                .roll(campaign, method);
    }

    /** Бросок предыстории: ответ странице, без перезагрузки */
    @PostMapping("/background")
    @ResponseBody
    public Object background(@PathVariable Long campaignId, @RequestParam(required = false) Integer age) {
        Campaign campaign = campaignService.getById(campaignId);
        return creationFor(campaign).orElseThrow(() -> new IllegalArgumentException("В этой системе нет мастера создания"))
                .background(campaign, age);
    }

    /**
     * Создание: ошибка ввода возвращает на страницу мастера с сообщением, введённое браузер сохраняет сам — форма шлётся без перезагрузки.
     */
    @PostMapping
    public String create(@PathVariable Long campaignId, @RequestParam Map<String, String> params,
                         RedirectAttributes redirectAttributes) {
        Campaign campaign = campaignService.getById(campaignId);
        CharacterCreation creation = creationFor(campaign).orElseThrow(() -> new IllegalArgumentException("В этой системе нет мастера создания"));
        try {
            Character created = creation.create(campaign, params);
            return "redirect:/character/" + created.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/campaign/" + campaignId + "/characters/new";
        }
    }

    private Optional<CharacterCreation> creationFor(Campaign campaign) {
        return creations.stream().filter(creation -> creation.getSystemCode().equals(campaign.getSystem())).findFirst();
    }
}
