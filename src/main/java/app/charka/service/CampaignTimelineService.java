package app.charka.service;

import app.charka.model.Campaign;
import app.charka.model.Chronicle;
import app.charka.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class CampaignTimelineService {
    private CampaignRepository campaignRepository;
    private ChronicleRepository chronicleRepository;
    private CharacterRepository characterRepository;
    private MoneyRepository moneyRepository;
    private WoundRepository woundRepository;

    public CampaignTimelineService(ChronicleRepository chronicleRepository, MoneyRepository moneyRepository, CampaignRepository campaignRepository) {
        this.chronicleRepository = chronicleRepository;
        this.moneyRepository = moneyRepository;
        this.campaignRepository = campaignRepository;

    }

    public void proceedDate(Campaign campaign, Integer daysToSkip, String description) {
        LocalDate currentDate = campaign.getCurrentDate();

        Chronicle chronicle = new Chronicle();
        chronicle.setCampaign(campaign);
        chronicle.setName(description);
        chronicle.setStartDate(currentDate);
        chronicle.setEndDate(currentDate.plusDays(daysToSkip));
        chronicleRepository.save(chronicle);

        campaign.setCurrentDate(currentDate.plusDays(daysToSkip));
        campaign.getChronicles().add(chronicle);
        campaignRepository.save(campaign);

        // Здесь будет реализация автоматических трат

        // Здесь будет реализация заживания ран

    }

}
