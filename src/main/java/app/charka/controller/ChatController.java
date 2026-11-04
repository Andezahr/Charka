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
    private final app.charka.dice.DiceService diceService;
    private final app.charka.skill.SkillService skillService;
    private final app.charka.check.CheckService checkService;

    /** Только лента: её подменяет опрос */
    @GetMapping
    public String feed(@PathVariable Long campaignId, Model model) {
        model.addAttribute("feed", chatService.feedFor(campaignService.getById(campaignId), FEED_SIZE));
        model.addAttribute("campaignId", campaignId);
        return "fragments/chat :: feed";
    }

    /** R-04: «Стол» для выезжающей панели — на страницах, где своей карточки чата нет */
    @GetMapping("/panel")
    public String panel(@PathVariable Long campaignId, @RequestParam(required = false) String returnTo, Model model) {
        app.charka.model.Campaign campaign = campaignService.getById(campaignId);
        boolean gm = accessService.isGm(campaign);
        model.addAttribute("feed", chatService.feedFor(campaign, FEED_SIZE));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("returnTo", back(campaignId, returnTo));
        model.addAttribute("chatCharacters", accessService.visible(campaign));
        model.addAttribute("isGm", gm);
        return "fragments/chat :: panel";
    }

    /**
     * R-04: «Стол» — одно поле на сообщение и бросок. Текст, который начинается с выражения кубиков («2d6+3 Атака»),
     * бросается, остальное уходит сообщением; быстрые кнопки шлют {@code quick}. Бросок мастера можно скрыть (A-09).
     *
     * @param returnTo куда вернуться: страница этой кампании или персонажа; иначе кампания
     */
    @PostMapping
    public String post(@PathVariable Long campaignId, @RequestParam(required = false) Long character,
                       @RequestParam(required = false) String text, @RequestParam(required = false) String quick,
                       @RequestParam(defaultValue = "false") boolean hidden,
                       @RequestParam(required = false) String returnTo, RedirectAttributes redirectAttributes) {
        String input = quick != null && !quick.isBlank() ? quick : text == null ? "" : text;
        try {
            // U-03: за мастера пишет мастер, за персонажа — его игрок
            app.charka.model.Campaign campaign = campaignService.getById(campaignId);
            boolean gm = accessService.isGm(campaign);
            boolean allowed = character == null ? gm
                    : characterRepository.findById(character).filter(c -> c.getCampaign() != null
                            && c.getCampaign().getId().equals(campaignId) && accessService.canControl(c)).isPresent();
            if (!allowed) {
                throw new IllegalArgumentException("Писать можно только от себя");
            }
            String[] roll = rollOf(input);
            if (roll != null) {
                app.charka.dice.HiddenRolls.run(hidden && gm, () -> character == null
                        ? diceService.rollForCampaign(campaignId, roll[0], roll[1])
                        : diceService.rollForCharacter(character, roll[0], roll[1]));
            } else {
                chatService.post(campaignId, character, input);
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("chatError", e.getMessage());
            redirectAttributes.addFlashAttribute("chatDraft", text);
        }
        return "redirect:" + back(campaignId, returnTo) + "#chat";
    }

    /**
     * R-04a: проверка из «Стола». У персонажа и системы с каталогом — проверка навыка (итог уже сложен), иначе — мастер
     * бросает «±Мод против DC» по правилам системы. Итог уходит в ленту (лог бросков).
     *
     * @param skill код навыка или {@code custom:<id>}
     */
    @PostMapping("/check")
    public String check(@PathVariable Long campaignId, @RequestParam(required = false) Long character,
                        @RequestParam(required = false) String skill, @RequestParam(required = false) Integer difficulty,
                        @RequestParam(defaultValue = "0") int modifier, @RequestParam(defaultValue = "0") int luck,
                        @RequestParam(required = false) String label, @RequestParam(defaultValue = "false") boolean hidden,
                        @RequestParam(required = false) String returnTo, RedirectAttributes redirectAttributes) {
        try {
            app.charka.model.Campaign campaign = campaignService.getById(campaignId);
            boolean gm = accessService.isGm(campaign);
            if (character != null && skill != null && !skill.isBlank()) {
                app.charka.model.Character who = characterRepository.findById(character)
                        .filter(c -> c.getCampaign() != null && c.getCampaign().getId().equals(campaignId) && accessService.canControl(c))
                        .orElseThrow(() -> new IllegalArgumentException("Проверять можно только за себя"));
                app.charka.dice.HiddenRolls.run(hidden && gm, () -> skill.startsWith("custom:")
                        ? skillService.check(who.getId(), null, Long.valueOf(skill.substring("custom:".length())), modifier, difficulty, Math.max(0, luck))
                        : skillService.check(who.getId(), skill, null, modifier, difficulty, Math.max(0, luck)));
            } else if (gm) {
                app.charka.check.CheckRequest request = new app.charka.check.CheckRequest(label, modifier, difficulty);
                app.charka.dice.HiddenRolls.run(hidden, () -> checkService.checkForCampaign(campaignId, request));
            } else {
                throw new IllegalArgumentException("Выберите, кто проверяет");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("chatError", e.getMessage());
        }
        return "redirect:" + back(campaignId, returnTo) + "#chat";
    }

    /** Выражение кубиков в начале текста и подпись после него; null — это сообщение */
    static String[] rollOf(String input) {
        String trimmed = input.strip();
        int space = trimmed.indexOf(' ');
        String head = space < 0 ? trimmed : trimmed.substring(0, space);
        if (head.isEmpty() || !head.matches("(?i)\\d*d\\d+([+-]\\d+)?")) {
            return null;
        }
        try {
            app.charka.dice.DiceExpression.parse(head);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String label = space < 0 ? null : trimmed.substring(space + 1).strip();
        return new String[]{head, label == null || label.isEmpty() ? null : label};
    }

    /** Своя страница кампании или персонажа — туда; чужой адрес — на кампанию */
    private static String back(Long campaignId, String returnTo) {
        String base = "/campaign/" + campaignId;
        if (returnTo != null && returnTo.matches("/(campaign/" + campaignId + "|character/\\d+)(/[\\w/-]*)?")) {
            return returnTo;
        }
        return base;
    }
}
