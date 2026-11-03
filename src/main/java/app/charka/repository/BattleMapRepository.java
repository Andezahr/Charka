package app.charka.repository;

import app.charka.model.BattleMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BattleMapRepository extends JpaRepository<BattleMap, Long> {
    List<BattleMap> findByCampaignIdOrderByNameAsc(Long campaignId);
}
