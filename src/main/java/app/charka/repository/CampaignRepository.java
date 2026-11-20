package app.charka.repository;

import app.charka.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    java.util.Optional<Campaign> findByInviteToken(String inviteToken);

    /** PH-21: код системы кампании без загрузки самой кампании (тема страницы) */
    @org.springframework.data.jpa.repository.Query("select c.system from Campaign c where c.id = :id")
    java.util.Optional<String> findSystemCode(@org.springframework.data.repository.query.Param("id") Long id);
}