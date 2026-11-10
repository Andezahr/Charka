package app.charka.repository;

import app.charka.model.RollRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RollRequestRepository extends JpaRepository<RollRequest, Long> {
    List<RollRequest> findByCampaignIdAndDoneAtIsNullOrderByIdAsc(Long campaignId);

    List<RollRequest> findByGroupKeyOrderByIdAsc(String groupKey);

    /** UX-21a: коды навыков, которые мастер просил в этой кампании, — самые частые первыми */
    @org.springframework.data.jpa.repository.Query("select r.code from RollRequest r where r.campaignId = :campaignId and r.kind = 'SKILL' "
            + "group by r.code order by count(r) desc, max(r.id) desc")
    List<String> frequentSkillCodes(@org.springframework.data.repository.query.Param("campaignId") Long campaignId);
}
