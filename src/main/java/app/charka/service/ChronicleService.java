package app.charka.service;

import app.charka.model.Chronicle;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ChronicleService {
    Chronicle create(Long campaignId, Chronicle chronicle);
    Chronicle update(Long id, Chronicle updatedChronicle);
    void delete(Long id);
    Optional<Chronicle> findById(Long id);
    List<Chronicle> getByCampaign(Long campaignId);
    List<Chronicle> findByCampaignAndDate(Long campaignId, LocalDate date);
}
