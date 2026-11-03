package app.charka.security;

import app.charka.creation.CharacterCreation;
import app.charka.model.AppUser;
import app.charka.model.Campaign;
import app.charka.model.Character;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Приглашение (A-02). {@code /join/{token}} требует входа: Spring отправит на вход и вернёт сюда,
 * а там же можно зарегистрироваться одним вводом пароля. Здесь — выбор свободного персонажа или мастер создания.
 */
@Controller
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;
    private final AccessService accessService;
    private final List<CharacterCreation> creations;

    @GetMapping("/join/{token}")
    public String join(@PathVariable String token, HttpSession session, Model model) {
        Campaign campaign = inviteService.campaign(token);
        rememberInvite(session, campaign.getId());
        Long me = accessService.current().map(AppUser::getId).orElse(null);
        model.addAttribute("campaign", campaign);
        model.addAttribute("token", token);
        model.addAttribute("isGm", accessService.isGm(campaign) && accessService.isEnabled());
        model.addAttribute("securityOn", accessService.isEnabled());
        model.addAttribute("free", inviteService.free(campaign));
        model.addAttribute("mine", campaign.getCharacters().stream()
                .filter(character -> me != null && character.getPlayer() != null && me.equals(character.getPlayer().getId()))
                .toList());
        model.addAttribute("hasCreation", creations.stream().anyMatch(creation -> creation.getSystemCode().equals(campaign.getSystem())));
        return "join";
    }

    @PostMapping("/join/{token}/claim/{characterId}")
    public String claim(@PathVariable String token, @PathVariable Long characterId, RedirectAttributes redirectAttributes) {
        try {
            Character character = inviteService.claim(token, characterId);
            return "redirect:/character/" + character.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/join/" + token;
        }
    }

    /** Мастер: старая ссылка перестаёт работать */
    @PostMapping("/campaign/{campaignId}/invite/reset")
    public String reset(@PathVariable Long campaignId) {
        inviteService.reset(campaignId);
        return "redirect:/campaign/" + campaignId + "/settings#invite";
    }

    @SuppressWarnings("unchecked")
    private static void rememberInvite(HttpSession session, Long campaignId) {
        Set<Long> invited = session.getAttribute(InviteService.INVITED) instanceof Set<?> set ? (Set<Long>) set : new HashSet<>();
        invited.add(campaignId);
        session.setAttribute(InviteService.INVITED, invited);
    }
}
