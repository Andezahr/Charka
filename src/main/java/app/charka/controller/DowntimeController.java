package app.charka.controller;

import app.charka.service.campaign.CampaignService;
import app.charka.service.campaign.DowntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Даунтайм (A-10): форма «сколько дней, кто работал, кто лечит» и итог по персонажам с отменой.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/campaign/{campaignId}/downtime")
public class DowntimeController {

    private static final String HEALER = "healer.";

    private final DowntimeService downtimeService;
    private final CampaignService campaignService;

    @GetMapping
    public String page(@PathVariable Long campaignId, Model model) {
        model.addAttribute("campaign", campaignService.getById(campaignId));
        model.addAttribute("rows", downtimeService.form(campaignId));
        return "downtime";
    }

    /**
     * @param worked кто работал (несколько)
     * @param params {@code healer.<id пациента>} — id медика; пусто — без медика
     */
    @PostMapping
    public String apply(@PathVariable Long campaignId, @RequestParam(required = false) Integer days,
                        @RequestParam(required = false) Integer customDays, @RequestParam(required = false) String name,
                        @RequestParam(required = false) List<Long> worked, @RequestParam Map<String, String> params,
                        RedirectAttributes redirectAttributes) {
        Map<Long, Long> healers = new HashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith(HEALER) && value != null && !value.isBlank()) {
                healers.put(Long.valueOf(key.substring(HEALER.length())), Long.valueOf(value));
            }
        });
        Set<Long> workers = worked == null ? Set.of() : new HashSet<>(worked);
        // кнопки «Неделя», «Месяц» несут число сами; «Промотать» — то, что введено
        int period = days != null ? days : customDays != null ? customDays : 0;
        try {
            redirectAttributes.addFlashAttribute("downtimeReport", downtimeService.apply(campaignId, period, name, workers, healers));
            redirectAttributes.addFlashAttribute("downtimeDays", period);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("downtimeError", e.getMessage());
        }
        return "redirect:/campaign/" + campaignId + "/downtime";
    }
}
