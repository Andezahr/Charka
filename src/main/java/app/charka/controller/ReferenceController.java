package app.charka.controller;

import app.charka.service.campaign.CampaignService;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Справка правил (A-11): разделы от системы кампании ({@code GameSystem.getReference}), окно «📖 Правила».
 */
@Controller
@RequiredArgsConstructor
public class ReferenceController {

    private final CampaignService campaignService;
    private final GameSystemRegistry systemRegistry;

    @GetMapping("/campaign/{campaignId}/reference")
    public String reference(@PathVariable Long campaignId, Model model) {
        model.addAttribute("sections", systemRegistry.get(campaignService.getById(campaignId).getSystem()).getReference());
        return "fragments/reference :: reference-sections";
    }
}
