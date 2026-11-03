package app.charka.controller;

import app.charka.model.Character;
import app.charka.npc.NpcService;
import app.charka.service.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * NPC кампании; итог показывается на странице кампании.
 */
@Controller
@RequiredArgsConstructor
public class NpcController {

    private final NpcService npcService;
    private final CharacterService characterService;

    @PostMapping("/campaign/{campaignId}/npcs")
    public String create(@PathVariable Long campaignId,
                         @RequestParam(required = false) String template,
                         @RequestParam(required = false) String name,
                         @RequestParam(defaultValue = "1") int count,
                         @RequestParam(defaultValue = "false") boolean advanced,
                         RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("npcMessage", "Создано: " + names(npcService.create(campaignId, template, name, count, advanced)));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("npcError", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "#npcs";
    }

    @PostMapping("/character/{npcId}/npc/copy")
    public String copy(@PathVariable Long npcId,
                       @RequestParam(defaultValue = "1") int count,
                       RedirectAttributes redirectAttributes) {
        Long campaignId = campaignOf(npcId);
        try {
            redirectAttributes.addFlashAttribute("npcMessage", "Создано: " + names(npcService.copy(npcId, count)));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("npcError", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "#npcs";
    }

    @PostMapping("/character/{npcId}/npc/delete")
    public String delete(@PathVariable Long npcId, RedirectAttributes redirectAttributes) {
        Long campaignId = campaignOf(npcId);
        try {
            npcService.delete(npcId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("npcError", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "#npcs";
    }

    private Long campaignOf(Long characterId) {
        Character character = characterService.getById(characterId);
        if (character.getCampaign() == null) {
            throw new IllegalArgumentException("Персонаж вне кампании: " + character.getName());
        }
        return character.getCampaign().getId();
    }

    private static String names(List<Character> characters) {
        return characters.stream().map(Character::getName).collect(Collectors.joining(", "));
    }
}
