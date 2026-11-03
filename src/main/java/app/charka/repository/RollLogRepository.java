package app.charka.repository;

import app.charka.model.RollLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RollLogRepository extends JpaRepository<RollLog, Long> {

    List<RollLog> findByCampaignIdOrderByCreatedAtDescIdDesc(Long campaignId, Pageable pageable);

    /** Лента игрока: без скрытых бросков мастера (A-09) */
    List<RollLog> findByCampaignIdAndHiddenFalseOrderByCreatedAtDescIdDesc(Long campaignId, Pageable pageable);

    /** Страница лога кампании, новые сверху; с общим числом бросков для листалки */
    org.springframework.data.domain.Page<RollLog> findPageByCampaignIdOrderByCreatedAtDescIdDesc(Long campaignId, Pageable pageable);

    List<RollLog> findByCharacterIdOrderByCreatedAtDescIdDesc(Long characterId, Pageable pageable);
}
