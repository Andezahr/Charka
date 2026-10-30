package app.charka.service;

import app.charka.model.Chronicle;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ChronicleService {

    @Transactional
    Chronicle create(Long campaignId, Chronicle chronicle);

    @Transactional
    Chronicle update(Long id, Chronicle updatedChronicle);

    @Transactional
    void delete(Long id);

    @Transactional(readOnly = true)
    Optional<Chronicle> findById(Long id);

    @Transactional(readOnly = true)
    List<Chronicle> getByCampaign(Long campaignId);

    @Transactional(readOnly = true)
    List<Chronicle> findByCampaignAndDate(Long campaignId, LocalDate date);
}
