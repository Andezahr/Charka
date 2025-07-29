package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;

    @Transactional
    public Campaign create(Campaign campaign) {
        return campaignRepository.save(campaign);
    }

    public Campaign getById(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found for ID: " + id));
    }

    public List<Campaign> getAll() {
        return campaignRepository.findAll();
    }

    @Transactional
    public Campaign rename(Long id, String newName) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found for ID: " + id));
        campaign.setName(newName);
        return campaignRepository.save(campaign);
    }

    /**
     * Обновляет текущую дату кампании.
     */
    @Transactional
    public void updateCurrentDate(Long id, LocalDate newDate) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found for ID: " + id));
        campaign.setCurrentDate(newDate);
        campaignRepository.save(campaign);
    }

    @Transactional
    public void delete(Long id) {
        campaignRepository.deleteById(id);
    }
}
