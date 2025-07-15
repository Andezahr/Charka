package app.Charka.controller;

import app.Charka.model.Campaign;

import app.Charka.repository.ChronicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import app.Charka.model.Chronicle;
import app.Charka.repository.CampaignRepository;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

@Controller
@RequestMapping("/campaign")
public class CampaignController {

    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private ChronicleRepository chronicleRepository;

    // Страница компании: название, персонажи, их раны, текущая дата
    @GetMapping("/{campaignId}")
    public String campaignPage(@PathVariable Long campaignId, Model model) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Кампания не найдена: " + campaignId));

        // Текущая дата кампании — максимальная endDate из хрониклов, иначе startDate
        LocalDate currentDate = chronicleRepository.findMaxEndDateByCampaignId(campaignId);
        if (campaign.getChronicles() != null && !campaign.getChronicles().isEmpty()) {
            Optional<LocalDate> maxEnd = campaign.getChronicles().stream()
                    .map(Chronicle::getEndDate)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());
            if (maxEnd.isPresent()) {
                currentDate = maxEnd.get();
            }
        }

        model.addAttribute("campaign", campaign);
        model.addAttribute("characters", campaign.getCharacters());
        model.addAttribute("currentDate", currentDate);

        return "campaign";
    }
}