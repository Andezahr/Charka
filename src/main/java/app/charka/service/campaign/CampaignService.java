package app.charka.service.campaign;

import app.charka.exception.NotFoundException;
import app.charka.model.Campaign;
import app.charka.repository.CampaignRepository;
import app.charka.system.GameSystemRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final GameSystemRegistry systemRegistry;

    /**
     * Сохраняет кампанию. Система должна быть зарегистрирована; без даты начала — сегодняшняя,
     * текущая игровая дата по умолчанию равна дате начала.
     *
     * @throws IllegalArgumentException если система неизвестна
     */
    @Transactional
    public Campaign create(Campaign campaign) {
        campaign.setSystem(systemRegistry.get(campaign.getSystem()).getCode());
        if (campaign.getStartDate() == null) {
            campaign.setStartDate(LocalDate.now());
        }
        if (campaign.getCurrentDate() == null) {
            campaign.setCurrentDate(campaign.getStartDate());
        }
        return campaignRepository.save(campaign);
    }

    public Campaign getById(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Campaign not found for ID: " + id));
    }

    public List<Campaign> getAll() {
        return campaignRepository.findAll();
    }

    @Transactional
    public Campaign rename(Long id, String newName) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Campaign not found for ID: " + id));
        campaign.setName(newName);
        return campaignRepository.save(campaign);
    }

    /**
     * Обновляет текущую дату кампании.
     */
    @Transactional
    public void updateCurrentDate(Long id, LocalDate newDate) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Campaign not found for ID: " + id));
        campaign.setCurrentDate(newDate);
        campaignRepository.save(campaign);
    }

    @Transactional
    public void delete(Long id) {
        campaignRepository.deleteById(id);
    }
}
