package app.charka.controller;

import app.charka.dice.DiceService;
import app.charka.model.Campaign;

import app.charka.model.Character;
import app.charka.service.CharacterService;
import app.charka.service.campaign.CampaignAwardService;
import app.charka.service.campaign.CampaignService;
import app.charka.system.GameSystem;
import app.charka.system.GameSystemRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/campaign/{campaignId}")
public class CampaignController {

    /** Бросков на странице лога */
    private static final int ROLLS_PER_PAGE = 10;

    private final CampaignService campaignService;
    private final CharacterService characterService;
    private final GameSystemRegistry systemRegistry;
    private final DiceService diceService;
    private final CampaignAwardService awardService;
    private final app.charka.chat.ChatService chatService;
    private final app.charka.security.AccessService accessService;
    private final app.charka.repository.AppUserRepository userRepository;
    private final List<app.charka.creation.CharacterCreation> creations;
    private final app.charka.security.InviteService inviteService;

    public CampaignController(CampaignService campaignService, CharacterService characterService,
                              GameSystemRegistry systemRegistry, DiceService diceService, CampaignAwardService awardService,
                              List<app.charka.creation.CharacterCreation> creations, app.charka.chat.ChatService chatService, app.charka.security.AccessService accessService, app.charka.repository.AppUserRepository userRepository,
                              app.charka.security.InviteService inviteService) {
        this.creations = creations;
        this.inviteService = inviteService;
        this.campaignService = campaignService;
        this.characterService = characterService;
        this.systemRegistry = systemRegistry;
        this.diceService = diceService;
        this.awardService = awardService;
        this.chatService = chatService;
        this.accessService = accessService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String campaignPage(@PathVariable Long campaignId, @RequestParam(defaultValue = "0") int rolls, Model model,
                               jakarta.servlet.http.HttpServletRequest request) {
        Campaign campaign = campaignService.getById(campaignId);

        LocalDate currentDate = campaign.getCurrentDate();

        model.addAttribute("campaign", campaign);
        // U-03: игрок видит своих персонажей, мастер — всех и свои инструменты
        List<Character> visible = accessService.visible(campaign);
        model.addAttribute("players", visible.stream().filter(character -> !character.isNpc()).toList());
        model.addAttribute("npcs", visible.stream().filter(Character::isNpc).toList());
        model.addAttribute("isGm", accessService.isGm(campaign));
        model.addAttribute("securityOn", accessService.isEnabled());
        model.addAttribute("chatCharacters", visible);
        model.addAttribute("currentDate", currentDate);
        GameSystem gameSystem = systemRegistry.find(campaign.getSystem()).orElseGet(systemRegistry::getGeneric);
        model.addAttribute("gameSystem", gameSystem);
        model.addAttribute("npcTemplates", gameSystem.getNpcTemplates());
        model.addAttribute("briefs", campaign.getCharacters().stream()
                .collect(Collectors.toMap(Character::getId, gameSystem::getCharacterBrief)));
        model.addAttribute("quickDice", gameSystem.getQuickDice());
        model.addAttribute("difficultyPresets", gameSystem.getCheckResolver().getDifficultyPresets());
        org.springframework.data.domain.Page<app.charka.model.RollLog> rollPage = diceService.pageForCampaign(campaignId, rolls, ROLLS_PER_PAGE);
        model.addAttribute("recentRolls", rollPage.getContent());
        model.addAttribute("rollPage", rollPage);
        model.addAttribute("feed", chatService.feed(campaignId, ChatController.FEED_SIZE, accessService.isGm(campaign)));
        model.addAttribute("awardCounters", awardService.awardableCounters(campaignId));
        model.addAttribute("hasCreation", creations.stream().anyMatch(creation -> creation.getSystemCode().equals(campaign.getSystem())));
        // A-02: ссылка-приглашение и QR-код — мастеру, по адресу ноутбука в локальной сети
        if (accessService.isEnabled() && accessService.isGm(campaign)) {
            String path = "/join/" + inviteService.token(campaignId);
            model.addAttribute("invites", inviteService.addresses(request.getScheme(), request.getServerName(), request.getServerPort()).stream()
                    .map(base -> java.util.Map.of("url", base + path, "svg", inviteService.qrSvg(base + path)))
                    .toList());
        }

        return "campaign";
    }

    /**
     * U-03: отдать персонажа игроку по логину; пустой логин — персонаж снова у мастера.
     */
    @PostMapping("/characters/{characterId}/player")
    @org.springframework.transaction.annotation.Transactional
    public String assignPlayer(@PathVariable Long campaignId, @PathVariable Long characterId, @RequestParam(defaultValue = "") String username,
                               RedirectAttributes redirectAttributes) {
        Character character = campaignService.getById(campaignId).getCharacters().stream()
                .filter(candidate -> candidate.getId().equals(characterId)).findFirst()
                .orElseThrow(app.charka.exception.NotFoundException.forId("Character", characterId));
        String login = username.strip().toLowerCase(java.util.Locale.ROOT);
        if (login.isEmpty()) {
            character.setPlayer(null);
        } else {
            userRepository.findByUsername(login).ifPresentOrElse(character::setPlayer,
                    () -> redirectAttributes.addFlashAttribute("playerError", "Нет пользователя «" + login + "»: пусть сначала зарегистрируется"));
        }
        return "redirect:/campaign/" + campaignId;
    }

    /**
     * Начислить всем выбранным: итог построчно во flash {@code awardResult}, ошибка — {@code errorMessage}.
     */
    @PostMapping("/award")
    public String award(@PathVariable Long campaignId, @RequestParam String counter, @RequestParam int amount,
                        @RequestParam(name = "character", required = false) List<Long> characters,
                        RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("awardResult", awardService.award(campaignId, counter, amount, characters));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "#award";
    }

    @PostMapping("/characters")
    public String addCharacterToCampaign(@PathVariable Long campaignId, @RequestParam String name) {
        Character newCharacter = new Character();
        newCharacter.setName(name);
        characterService.createInCampaign(campaignId, newCharacter);
        return "redirect:/campaign/" + campaignId;
    }
}