package app.charka.repository;

import app.charka.model.RollRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RollRequestRepository extends JpaRepository<RollRequest, Long> {
    List<RollRequest> findByCampaignIdAndDoneAtIsNullOrderByIdAsc(Long campaignId);

    List<RollRequest> findByGroupKeyOrderByIdAsc(String groupKey);
}
