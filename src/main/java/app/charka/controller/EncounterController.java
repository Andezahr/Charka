package app.charka.controller;

import app.charka.attack.AttackResult;
import app.charka.attack.AttackService;
import app.charka.encounter.EncounterService;
import app.charka.encounter.EncounterView;
import app.charka.model.Campaign;
import app.charka.model.counters.strategy.health.HealthEvent;
import app.charka.service.campaign.CampaignService;
import app.charka.skill.SkillCheck;
import app.charka.check.OpposedCheckResult;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Боевой трекер кампании. Итог действия показывается в строке участника.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/encounter")
public class EncounterController {

    private final EncounterService encounterService;
    private final CampaignService campaignService;
    private final GameSystemRegistry systemRegistry;
    private final AttackService attackService;
    private final app.charka.map.BattleFieldService battleFieldService;
    private final app.charka.map.BattleMapService battleMapService;
    private final app.charka.chat.ChatService chatService;
    private final app.charka.security.AccessService accessService;

    @GetMapping
    public String page(@PathVariable Long campaignId, Model model) {
        Campaign campaign = campaignService.getById(campaignId);
        model.addAttribute("campaign", campaign);
        // куда вернуть атаку и действия с оружием
        model.addAttribute("returnCampaign", campaignId);
        Optional<EncounterView> view = encounterService.view(campaignId);
        if (view.isPresent()) {
            model.addAttribute("view", view.get());
            model.addAttribute("attackObjects", attackService.objectTargets(campaign));
            model.addAttribute("hasField", view.get().encounter().getMapDoc() != null);
            model.addAttribute("npcTemplates", systemRegistry.get(campaign.getSystem()).getNpcTemplates());
            model.addAttribute("feed", chatService.feed(campaignId, ChatController.FEED_SIZE));
            // U-03: игрок действует своими персонажами, остальные строки — только смотрит
            model.addAttribute("controllable", view.get().rows().stream().map(row -> row.character())
                    .filter(accessService::canControl).map(app.charka.model.Character::getId).collect(java.util.stream.Collectors.toSet()));
            model.addAttribute("difficultyPresets", systemRegistry.get(campaign.getSystem()).getCheckResolver().getDifficultyPresets());
            model.addAttribute("contests", systemRegistry.get(campaign.getSystem()).getCombatContests());
            app.charka.system.GameSystem system = systemRegistry.get(campaign.getSystem());
            model.addAttribute("luckLeft", view.get().rows().stream().collect(java.util.stream.Collectors.toMap(
                    row -> row.character().getId(), row -> system.getLuck(row.character()), (first, second) -> first)));
        } else {
            model.addAttribute("candidates", encounterService.candidates(campaignId));
        }
        model.addAttribute("maps", battleMapService.list(campaignId));
        model.addAttribute("isGm", accessService.isGm(campaign));
        model.addAttribute("chatCharacters", accessService.visible(campaign));
        return "encounter";
    }

    @PostMapping("/start")
    public String start(@PathVariable Long campaignId,
                        @RequestParam(required = false) List<Long> character,
                        @RequestParam(defaultValue = "false") boolean groupPlayers,
                        @RequestParam(defaultValue = "false") boolean groupNpcs,
                        @RequestParam(required = false) Long map,
                        RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> encounterService.start(campaignId, character == null ? List.of() : character, groupPlayers, groupNpcs, map));
    }

    /** M-11: мастер правит копию карты идущего боя теми же инструментами */
    @GetMapping("/edit-map")
    public String editMap(@PathVariable Long campaignId, Model model) {
        model.addAttribute("campaign", campaignService.getById(campaignId));
        model.addAttribute("docUrl", "/campaign/" + campaignId + "/encounter/map/doc");
        model.addAttribute("backUrl", "/campaign/" + campaignId + "/encounter");
        model.addAttribute("inFight", true);
        return "map-editor";
    }

    /** Положить карту под идущий бой: фишки расставляются заново */
    @PostMapping("/attach")
    public String attach(@PathVariable Long campaignId, @RequestParam Long map, RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            battleFieldService.attach(campaignId, map);
            return null;
        });
    }

    /** Копия карты боя становится заготовкой кампании */
    @PostMapping("/save-map")
    public String saveMap(@PathVariable Long campaignId, @RequestParam String name, RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            battleFieldService.saveAsMap(campaignId, name);
            redirectAttributes.addFlashAttribute("mapSaved", name.strip());
            return null;
        });
    }

    @PostMapping("/next")
    public String next(@PathVariable Long campaignId, RedirectAttributes redirectAttributes) {
        try {
            EncounterService.Turn turn = encounterService.next(campaignId);
            redirectAttributes.addFlashAttribute("roundReport", turn.report());
            return redirect(campaignId, turn.character());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("encounterError", e.getMessage());
            return redirect(campaignId, null);
        }
    }

    @PostMapping("/end")
    public String end(@PathVariable Long campaignId) {
        encounterService.end(campaignId);
        return "redirect:/campaign/" + campaignId;
    }

    @PostMapping("/participants")
    public String add(@PathVariable Long campaignId, @RequestParam Long character, RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.add(campaignId, character);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/remove")
    public String remove(@PathVariable Long campaignId, @PathVariable Long participantId, RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.remove(campaignId, participantId);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/wait")
    public String waitTurn(@PathVariable Long campaignId, @PathVariable Long participantId, RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.waitTurn(campaignId, participantId);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/join")
    public String join(@PathVariable Long campaignId, @PathVariable Long participantId, RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.join(campaignId, participantId);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/declare/{code}")
    public String declare(@PathVariable Long campaignId, @PathVariable Long participantId, @PathVariable String code,
                          RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.declare(campaignId, participantId, code);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/check")
    public String check(@PathVariable Long campaignId, @PathVariable Long participantId, @RequestParam String skill,
                        @RequestParam(required = false) Integer difficulty, @RequestParam(defaultValue = "0") int modifier,
                        @RequestParam(defaultValue = "0") int luck, RedirectAttributes redirectAttributes) {
        Long character = encounterService.characterOf(campaignId, participantId);
        redirectAttributes.addFlashAttribute("resultCharacterId", character);
        try {
            SkillCheck check = encounterService.check(campaignId, participantId, skill, difficulty, modifier, luck);
            redirectAttributes.addFlashAttribute("combatResult", new AttackResult(check.label(), check.result().outcome(),
                    !Boolean.FALSE.equals(check.result().success()), check.result().steps()));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("encounterError", e.getMessage());
        }
        return redirect(campaignId, character);
    }

    /**
     * Противостояние с другим участником (CP2020: Facedown): итог в строке начавшего.
     */
    @PostMapping("/participants/{participantId}/contest/{code}")
    public String contest(@PathVariable Long campaignId, @PathVariable Long participantId, @PathVariable String code,
                          @RequestParam Long target, @RequestParam(defaultValue = "0") int luck, RedirectAttributes redirectAttributes) {
        Long character = encounterService.characterOf(campaignId, participantId);
        redirectAttributes.addFlashAttribute("resultCharacterId", character);
        try {
            OpposedCheckResult result = encounterService.contest(campaignId, participantId, code, target, luck);
            List<String> steps = new ArrayList<>();
            steps.add(result.initiator().title() + ": " + result.initiator().result().outcome());
            result.initiator().result().steps().forEach(step -> steps.add("  " + step));
            steps.add(result.opponent().title() + ": " + result.opponent().result().outcome());
            result.opponent().result().steps().forEach(step -> steps.add("  " + step));
            redirectAttributes.addFlashAttribute("combatResult", new AttackResult(result.initiator().skill(), result.verdict(),
                    result.winner() == OpposedCheckResult.Winner.INITIATOR, steps));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("encounterError", e.getMessage());
        }
        return redirect(campaignId, character);
    }

    @PostMapping("/participants/{participantId}/effects/{effectId}/remove")
    public String removeEffect(@PathVariable Long campaignId, @PathVariable Long participantId, @PathVariable Long effectId,
                               RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.removeEffect(campaignId, participantId, effectId);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/initiative")
    public String initiative(@PathVariable Long campaignId, @PathVariable Long participantId, @RequestParam int initiative,
                             RedirectAttributes redirectAttributes) {
        return attempt(campaignId, redirectAttributes, () -> {
            encounterService.setInitiative(campaignId, participantId, initiative);
            return null;
        });
    }

    @PostMapping("/participants/{participantId}/damage")
    public String damage(@PathVariable Long campaignId, @PathVariable Long participantId,
                         @RequestParam int amount,
                         @RequestParam(required = false) String location,
                         @RequestParam(required = false) String damageType,
                         RedirectAttributes redirectAttributes) {
        return health(campaignId, participantId, "Урон", redirectAttributes,
                () -> encounterService.damage(campaignId, participantId, amount, location, damageType));
    }

    @PostMapping("/participants/{participantId}/save/{code}")
    public String save(@PathVariable Long campaignId, @PathVariable Long participantId, @PathVariable String code,
                       @RequestParam(defaultValue = "0") int luck, RedirectAttributes redirectAttributes) {
        return health(campaignId, participantId, "Спасбросок", redirectAttributes, () -> encounterService.save(campaignId, participantId, code, luck));
    }

    @PostMapping("/participants/{participantId}/stabilize")
    public String stabilize(@PathVariable Long campaignId, @PathVariable Long participantId, RedirectAttributes redirectAttributes) {
        return health(campaignId, participantId, "Стабилизация", redirectAttributes, () -> encounterService.stabilize(campaignId, participantId));
    }

    private String health(Long campaignId, Long participantId, String title, RedirectAttributes redirectAttributes, Supplier<HealthEvent> action) {
        Long character = encounterService.characterOf(campaignId, participantId);
        redirectAttributes.addFlashAttribute("resultCharacterId", character);
        try {
            HealthEvent event = action.get();
            redirectAttributes.addFlashAttribute("combatResult", new AttackResult(title, event.getSummary(), true, event.getSteps()));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("encounterError", e.getMessage());
        }
        return redirect(campaignId, character);
    }

    private String attempt(Long campaignId, RedirectAttributes redirectAttributes, Supplier<Object> action) {
        try {
            action.get();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("encounterError", e.getMessage());
        }
        return redirect(campaignId, null);
    }

    private static String redirect(Long campaignId, Long character) {
        return "redirect:/campaign/" + campaignId + "/encounter" + (character == null ? "" : "#combatant-" + character);
    }
}
