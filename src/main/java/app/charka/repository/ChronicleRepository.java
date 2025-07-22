package app.charka.repository;

import app.charka.model.Chronicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ChronicleRepository extends JpaRepository<Chronicle, Long> {

    List<Chronicle> findByCampaignId(Long campaignId);

    /**
     * Найти хроники кампании, период которых включает заданную дату.
     *
     * @param campaignId идентификатор кампании
     * @param startDate  начальная дата или ранее
     * @param endDate    конечная дата или позже
     * @return список Chronicle, охватывающих дату
     */
    List<Chronicle> findByCampaignIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long campaignId, LocalDate startDate, LocalDate endDate);
}

