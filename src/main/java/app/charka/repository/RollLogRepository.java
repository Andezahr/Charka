package app.charka.repository;

import app.charka.model.RollLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RollLogRepository extends JpaRepository<RollLog, Long> {

    List<RollLog> findByCampaignIdOrderByCreatedAtDescIdDesc(Long campaignId, Pageable pageable);

    List<RollLog> findByCharacterIdOrderByCreatedAtDescIdDesc(Long characterId, Pageable pageable);
}
