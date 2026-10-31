package app.charka.repository;

import app.charka.model.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {
    Optional<Encounter> findByCampaignId(Long campaignId);
}
