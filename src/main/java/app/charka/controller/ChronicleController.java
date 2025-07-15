package app.charka.controller;

import app.charka.Routes;
import app.charka.model.Campaign;
import app.charka.model.Chronicle;
import app.charka.repository.CampaignRepository;
import app.charka.repository.ChronicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class ChronicleController {
    @Autowired
    private ChronicleRepository chronicleRepository;
    @Autowired
    private CampaignRepository campaignRepository;

    @PostMapping(Routes.CHRONICLE_ADD)
    public String addChronicle(
            @PathVariable Long campaignId,
            @RequestParam String name,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Кампания не найдена: " + campaignId));
        Chronicle chronicle = new Chronicle();
        chronicle.setName(name);
        chronicle.setStartDate(startDate);
        chronicle.setEndDate(endDate);
        chronicle.setCampaign(campaign);
        chronicleRepository.save(chronicle);
        return Routes.CAMPAIGN_REDIRECT + campaignId;
    }
}
