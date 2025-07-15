package app.Charka.repository;

import app.Charka.model.Chronicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface ChronicleRepository extends JpaRepository<Chronicle, Long> {
    @Query("SELECT MAX(c.endDate) FROM Chronicle c WHERE c.campaign.id = :campaignId")
    LocalDate findMaxEndDateByCampaignId(@Param("campaignId") Long campaignId);
}
