package app.charka.repository;

import app.charka.model.MapLoot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MapLootRepository extends JpaRepository<MapLoot, Long> {
    List<MapLoot> findByEncounterIdOrderByIdAsc(Long encounterId);
}
