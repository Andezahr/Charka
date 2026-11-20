package app.charka.repository;

import app.charka.model.Campaign;
import app.charka.model.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {
     List<Character> findByCampaign(Campaign campaign);

     boolean existsByCampaignIdAndPlayerId(Long campaignId, Long playerId);

     /** PH-21: код системы кампании персонажа (тема страницы) */
     @org.springframework.data.jpa.repository.Query("select ch.campaign.system from Character ch where ch.id = :id")
     java.util.Optional<String> findSystemCode(@org.springframework.data.repository.query.Param("id") Long id);
}