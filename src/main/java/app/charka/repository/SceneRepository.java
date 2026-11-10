package app.charka.repository;

import app.charka.model.Scene;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SceneRepository extends JpaRepository<Scene, Long> {
    List<Scene> findByCampaignIdOrderByNameAsc(Long campaignId);
}
