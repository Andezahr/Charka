package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.Character;
import app.charka.security.AccessService;
import app.charka.security.InviteService;
import app.charka.service.campaign.CampaignService;
import app.charka.system.GameSystemRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.Map;

/**
 * R-13: вкладка «Настройки» кампании — то, что мастер делает редко: название, игроки и их персонажи, приглашение, экспорт.
 * Только мастеру: адреса кампании вне списка игрока {@link app.charka.security.AccessInterceptor} закрывает сам.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}")
public class CampaignSettingsController {

    private final CampaignService campaignService;
    private final GameSystemRegistry systemRegistry;
    private final AccessService accessService;
    private final InviteService inviteService;

    @GetMapping("/settings")
    public String settings(@PathVariable Long campaignId, Model model, HttpServletRequest request) {
        Campaign campaign = campaignService.getById(campaignId);
        model.addAttribute("campaign", campaign);
        model.addAttribute("gameSystem", systemRegistry.find(campaign.getSystem()).orElseGet(systemRegistry::getGeneric));
        model.addAttribute("securityOn", accessService.isEnabled());
        model.addAttribute("players", campaign.getCharacters().stream()
                .filter(character -> !character.isNpc())
                .sorted(Comparator.comparing(Character::getName, Comparator.nullsLast(String::compareTo)))
                .toList());
        // A-02: ссылка-приглашение и QR-код по адресу ноутбука в локальной сети
        if (accessService.isEnabled()) {
            String path = "/join/" + inviteService.token(campaignId);
            model.addAttribute("invitePort", request.getServerPort());
            model.addAttribute("invites", inviteService.addresses(request.getScheme(), request.getServerName(), request.getServerPort()).stream()
                    .map(address -> Map.of("url", address.url() + path, "svg", inviteService.qrSvg(address.url() + path),
                            "label", address.label(), "recommended", address.recommended()))
                    .toList());
        }
        return "settings";
    }

    @PostMapping("/rename")
    public String rename(@PathVariable Long campaignId, @RequestParam String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > 255) {
            throw new IllegalArgumentException("Название — от 1 до 255 символов");
        }
        campaignService.rename(campaignId, trimmed);
        return "redirect:/campaign/" + campaignId + "/settings";
    }
}
