package app.charka.controller;

import app.charka.model.Campaign;
import app.charka.model.RollRequest;
import app.charka.request.RollRequestService;
import app.charka.security.AccessService;
import app.charka.service.campaign.CampaignService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Запросы бросков (A-07, A-08). Карточки опрашиваются ({@code [data-requests]} в app.js): игрок видит свои, мастер — все
 * открытые. Итог — в плашке внизу экрана и в ленте; возврат туда, откуда нажали.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/requests")
public class RollRequestController {

    private final RollRequestService requestService;
    private final CampaignService campaignService;
    private final AccessService accessService;

    /**
     * @param character только запросы этого персонажа (на его листе); нет — все, что видно смотрящему
     */
    @GetMapping
    public String list(@PathVariable Long campaignId, @RequestParam(required = false) Long character, Model model) {
        Campaign campaign = campaignService.getById(campaignId);
        boolean gm = accessService.isGm(campaign);
        List<RollRequest> requests = requestService.open(campaignId).stream()
                .filter(request -> character == null || request.getCharacter().getId().equals(character))
                .filter(request -> gm || accessService.canControl(request.getCharacter()))
                .toList();
        model.addAttribute("requests", requests);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("isGm", gm);
        return "fragments/requests :: list";
    }

    /**
     * Мастер просит бросок: {@code character} — несколько; {@code rollNow} — «Бросить за всех» сразу.
     */
    @PostMapping
    public String ask(@PathVariable Long campaignId, @RequestParam(name = "character", required = false) List<Long> characters,
                      @RequestParam String what, @RequestParam(required = false) Integer difficulty,
                      @RequestParam(defaultValue = "false") boolean rollNow, HttpServletRequest request,
                      RedirectAttributes redirectAttributes) {
        try {
            List<String> results = requestService.ask(campaignId, characters, what, difficulty, rollNow);
            redirectAttributes.addFlashAttribute("requestResults", results.isEmpty() ? List.of("Запрос отправлен") : results);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("requestError", e.getMessage());
        }
        return "redirect:" + Referers.back(request, "/campaign/" + campaignId);
    }

    /** Одно нажатие игрока (или мастера за него): всё уже подставлено */
    @PostMapping("/{requestId}/roll")
    public String roll(@PathVariable Long campaignId, @PathVariable Long requestId, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        RollRequest found = requestService.get(campaignId, requestId);
        try {
            if (!accessService.canControl(found.getCharacter())) {
                throw new IllegalArgumentException("Бросает игрок этого персонажа или мастер");
            }
            redirectAttributes.addFlashAttribute("requestResults", List.of(found.getCharacter().getName() + ": " + requestService.roll(campaignId, requestId)));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("requestError", e.getMessage());
        }
        return "redirect:" + Referers.back(request, "/campaign/" + campaignId);
    }

    @PostMapping("/{requestId}/cancel")
    public String cancel(@PathVariable Long campaignId, @PathVariable Long requestId, HttpServletRequest request) {
        requestService.cancel(campaignId, requestId);
        return "redirect:" + Referers.back(request, "/campaign/" + campaignId);
    }
}
