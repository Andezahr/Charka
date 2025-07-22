package app.charka.service;

import app.charka.model.Campaign;
import app.charka.model.Chronicle;
import app.charka.repository.CampaignRepository;
import app.charka.repository.ChronicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChronicleService {

    private static final String CHRONICLE_NOT_FOUND = "Chronicle not found for id=%d";
    private static final String CAMPAIGN_NOT_FOUND  = "Campaign not found for id=%d";

    private final ChronicleRepository chronicleRepository;
    private final CampaignRepository campaignRepository;

    /**
     * Создать новую хронику для указанной кампании.
     *
     * @param campaignId идентификатор кампании
     * @param chronicle  объект Chronicle с заполненными полями startDate, endDate и name
     * @return сохранённая Chronicle с присвоенным id и связью на Campaign
     * @throws IllegalArgumentException если кампания не найдена
     */
    @Transactional
    public Chronicle create(Long campaignId, Chronicle chronicle) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(CAMPAIGN_NOT_FOUND, campaignId)));
        chronicle.setCampaign(campaign);
        return chronicleRepository.save(chronicle);
    }

    /**
     * Обновить существующую хронику.
     *
     * @param id               идентификатор хроники
     * @param updatedChronicle объект Chronicle с новыми значениями startDate, endDate и name
     * @return обновлённая Chronicle
     * @throws IllegalArgumentException если хроника не найдена
     */
    @Transactional
    public Chronicle update(Long id, Chronicle updatedChronicle) {
        Chronicle existing = chronicleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(CHRONICLE_NOT_FOUND, id)));
        existing.setStartDate(updatedChronicle.getStartDate());
        existing.setEndDate(updatedChronicle.getEndDate());
        existing.setName(updatedChronicle.getName());
        // Кампанию не меняем; для переноса между кампаниями нужно отдельный метод
        return chronicleRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Chronicle existing = chronicleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(CHRONICLE_NOT_FOUND, id)));
        chronicleRepository.delete(existing);
    }

    @Transactional(readOnly = true)
    public Optional<Chronicle> findById(Long id) {
        return chronicleRepository.findById(id);
    }

    /**
     * Получить все хроники, связанные с кампанией.
     *
     * @param campaignId идентификатор кампании
     * @return список Chronicle; пустой список, если нет записей
     */
    @Transactional(readOnly = true)
    public List<Chronicle> getByCampaign(Long campaignId) {
        return chronicleRepository.findByCampaignId(campaignId);
    }

    /**
     * Получить все хроники кампании, охватывающие заданную дату.
     *
     * @param campaignId идентификатор кампании
     * @param date       дата для поиска (должна быть между startDate и endDate)
     * @return список Chronicle, период которых включает указанную дату
     */
    @Transactional(readOnly = true)
    public List<Chronicle> findByCampaignAndDate(Long campaignId, LocalDate date) {
        return chronicleRepository
                .findByCampaignIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        campaignId, date, date);
    }
}
