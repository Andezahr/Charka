package app.charka.controller;

import app.charka.map.BattleMapService;
import app.charka.map.MapDoc;
import app.charka.model.BattleMap;
import app.charka.service.campaign.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Карты боя кампании (M-06): список, создать, копировать, удалить; редактор.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/maps")
public class MapController {

    private final BattleMapService battleMapService;
    private final CampaignService campaignService;

    @GetMapping
    public String list(@PathVariable Long campaignId, Model model) {
        model.addAttribute("campaign", campaignService.getById(campaignId));
        Map<Long, MapDoc> docs = new LinkedHashMap<>();
        battleMapService.list(campaignId).forEach(map -> docs.put(map.getId(), MapDoc.parse(map.getDoc())));
        model.addAttribute("maps", battleMapService.list(campaignId));
        model.addAttribute("docs", docs);
        model.addAttribute("defaultWidth", BattleMapService.DEFAULT_WIDTH);
        model.addAttribute("defaultHeight", BattleMapService.DEFAULT_HEIGHT);
        return "maps";
    }

    @PostMapping
    public String create(@PathVariable Long campaignId, @RequestParam String name,
                         @RequestParam(defaultValue = "30") int width, @RequestParam(defaultValue = "20") int height,
                         RedirectAttributes redirectAttributes) {
        try {
            BattleMap map = battleMapService.create(campaignId, name, width, height);
            return "redirect:/campaign/" + campaignId + "/maps/" + map.getId() + "/edit";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mapError", e.getMessage());
            return "redirect:/campaign/" + campaignId + "/maps";
        }
    }

    @PostMapping("/{mapId}/copy")
    public String copy(@PathVariable Long campaignId, @PathVariable Long mapId) {
        battleMapService.copy(campaignId, mapId);
        return "redirect:/campaign/" + campaignId + "/maps";
    }

    @PostMapping("/{mapId}/rename")
    public String rename(@PathVariable Long campaignId, @PathVariable Long mapId, @RequestParam String name,
                         RedirectAttributes redirectAttributes) {
        try {
            battleMapService.rename(campaignId, mapId, name);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mapError", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "/maps";
    }

    @PostMapping("/{mapId}/delete")
    public String delete(@PathVariable Long campaignId, @PathVariable Long mapId) {
        battleMapService.delete(campaignId, mapId);
        return "redirect:/campaign/" + campaignId + "/maps";
    }
}
