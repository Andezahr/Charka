package app.charka.controller;

import app.charka.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Чат кампании (U-06): лента обновляется опросом фрагмента с ETag ({@code MapEtagFilter}), сообщение — обычной формой.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/chat")
public class ChatController {

    public static final int FEED_SIZE = 30;

    private final ChatService chatService;
    private final app.charka.security.AccessService accessService;
    private final app.charka.service.campaign.CampaignService campaignService;
    private final app.charka.repository.CharacterRepository characterRepository;

    /** Только лента: её подменяет опрос */
    @GetMapping
    public String feed(@PathVariable Long campaignId, Model model) {
        model.addAttribute("feed", chatService.feed(campaignId, FEED_SIZE, accessService.isGm(campaignService.getById(campaignId))));
        model.addAttribute("campaignId", campaignId);
        return "fragments/chat :: feed";
    }

    /**
     * @param returnTo куда вернуться: страница кампании или боя; иначе кампания
     */
    @PostMapping
    public String post(@PathVariable Long campaignId, @RequestParam(required = false) Long character, @RequestParam String text,
                       @RequestParam(required = false) String returnTo, RedirectAttributes redirectAttributes) {
        try {
            // U-03: за мастера пишет мастер, за персонажа — его игрок
            app.charka.model.Campaign campaign = campaignService.getById(campaignId);
            boolean allowed = character == null ? accessService.isGm(campaign)
                    : characterRepository.findById(character).filter(c -> c.getCampaign() != null
                            && c.getCampaign().getId().equals(campaignId) && accessService.canControl(c)).isPresent();
            if (!allowed) {
                throw new IllegalArgumentException("Писать можно только от себя");
            }
            chatService.post(campaignId, character, text);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("chatError", e.getMessage());
            redirectAttributes.addFlashAttribute("chatDraft", text);
        }
        String base = "/campaign/" + campaignId;
        boolean known = base.equals(returnTo) || (base + "/encounter").equals(returnTo);
        return "redirect:" + (known ? returnTo : base) + "#chat";
    }
}
