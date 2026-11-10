package app.charka.controller;

import app.charka.encounter.SceneService;
import app.charka.model.Campaign;
import app.charka.service.campaign.CampaignService;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.function.Supplier;

/**
 * Заготовки боя (A-06): список сцен кампании и правка; «Начать» — с этой страницы и со страницы боя без боя.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/scenes")
public class SceneController {

    private final SceneService sceneService;
    private final CampaignService campaignService;
    private final GameSystemRegistry systemRegistry;
    private final app.charka.map.BattleMapService battleMapService;
    private final app.charka.encounter.EncounterService encounterService;

    @GetMapping
    public String page(@PathVariable Long campaignId, Model model) {
        Campaign campaign = campaignService.getById(campaignId);
        model.addAttribute("campaign", campaign);
        model.addAttribute("scenes", sceneService.list(campaignId));
        model.addAttribute("fightGoing", encounterService.activeRound(campaignId) != null);
        model.addAttribute("maps", battleMapService.list(campaignId));
        model.addAttribute("npcTemplates", systemRegistry.get(campaign.getSystem()).getNpcTemplates());
        model.addAttribute("templateLabels", systemRegistry.get(campaign.getSystem()).getNpcTemplates().stream()
                .collect(java.util.stream.Collectors.toMap(app.charka.npc.NpcTemplate::code, app.charka.npc.NpcTemplate::label)));
        model.addAttribute("campaignNpcs", campaign.getCharacters().stream().filter(app.charka.model.Character::isNpc).toList());
        return "scenes";
    }

    @PostMapping
    public String create(@PathVariable Long campaignId, @RequestParam String name, @RequestParam(required = false) Long map,
                         RedirectAttributes redirectAttributes) {
        try {
            return "redirect:/campaign/" + campaignId + "/scenes#scene-" + sceneService.create(campaignId, name, map).getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("sceneError", e.getMessage());
            return "redirect:/campaign/" + campaignId + "/scenes";
        }
    }

    @PostMapping("/{sceneId}/edit")
    public String edit(@PathVariable Long campaignId, @PathVariable Long sceneId, @RequestParam String name,
                       @RequestParam(required = false) Long map, @RequestParam(defaultValue = "false") boolean withPlayers,
                       @RequestParam(defaultValue = "false") boolean groupPlayers, @RequestParam(defaultValue = "false") boolean groupNpcs,
                       RedirectAttributes redirectAttributes) {
        return attempt(campaignId, sceneId, redirectAttributes,
                () -> sceneService.update(campaignId, sceneId, name, map, withPlayers, groupPlayers, groupNpcs));
    }

    @PostMapping("/{sceneId}/delete")
    public String delete(@PathVariable Long campaignId, @PathVariable Long sceneId) {
        sceneService.delete(campaignId, sceneId);
        return "redirect:/campaign/" + campaignId + "/scenes";
    }

    /**
     * @param who «t:КОД» — по шаблону, «c:id» — существующий персонаж
     */
    @PostMapping("/{sceneId}/npcs")
    public String addNpc(@PathVariable Long campaignId, @PathVariable Long sceneId, @RequestParam String who,
                         @RequestParam(required = false) String name, @RequestParam(defaultValue = "1") int count,
                         @RequestParam(defaultValue = "false") boolean advanced, @RequestParam(defaultValue = "false") boolean ambush,
                         RedirectAttributes redirectAttributes) {
        return attempt(campaignId, sceneId, redirectAttributes, () -> {
            sceneService.addNpc(campaignId, sceneId, who, name, count, advanced, ambush);
            return null;
        });
    }

    @PostMapping("/{sceneId}/npcs/{npcId}/delete")
    public String removeNpc(@PathVariable Long campaignId, @PathVariable Long sceneId, @PathVariable Long npcId,
                            RedirectAttributes redirectAttributes) {
        return attempt(campaignId, sceneId, redirectAttributes, () -> {
            sceneService.removeNpc(campaignId, sceneId, npcId);
            return null;
        });
    }

    @PostMapping("/{sceneId}/npcs/{npcId}/ambush")
    public String ambush(@PathVariable Long campaignId, @PathVariable Long sceneId, @PathVariable Long npcId,
                         RedirectAttributes redirectAttributes) {
        return attempt(campaignId, sceneId, redirectAttributes, () -> {
            sceneService.toggleAmbush(campaignId, sceneId, npcId);
            return null;
        });
    }

    /** Одно нажатие: NPC создаются, бой начинается на карте сцены */
    @PostMapping("/{sceneId}/start")
    public String start(@PathVariable Long campaignId, @PathVariable Long sceneId, RedirectAttributes redirectAttributes) {
        try {
            sceneService.start(campaignId, sceneId);
            return "redirect:/campaign/" + campaignId + "/encounter";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("encounterError", e.getMessage());
            return "redirect:/campaign/" + campaignId + "/encounter";
        }
    }

    private String attempt(Long campaignId, Long sceneId, RedirectAttributes redirectAttributes, Supplier<Object> action) {
        try {
            action.get();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("sceneError", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "/scenes#scene-" + sceneId;
    }
}
