package app.charka.service.campaign;

import app.charka.model.Campaign;
import app.charka.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;

    @Transactional
    public Campaign create(Campaign campaign) {
        return campaignRepository.save(campaign);
    }

    public Optional<Campaign> getById(Long id) {
        return campaignRepository.findById(id);
    }

    public List<Campaign> getAll() {
        return campaignRepository.findAll();
    }

    @Transactional
    public Campaign rename(Long id, String newName) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        campaign.setName(newName);
        return campaignRepository.save(campaign);
    }

    /**
     * Обновляет текущую дату кампании.
     */
    @Transactional
    public Campaign updateCurrentDate(Long id, LocalDate newDate) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));
        campaign.setCurrentDate(newDate);
        return campaignRepository.save(campaign);
    }

    @Transactional
    public void delete(Long id) {
        campaignRepository.deleteById(id);
    }
}
