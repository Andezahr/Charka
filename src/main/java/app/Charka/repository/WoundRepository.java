package app.Charka.repository;

import app.Charka.model.Wound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WoundRepository extends JpaRepository<Wound, Long> {
    List<Wound> findByCharacterId(Long characterId);
}
