package app.charka.repository;

import app.charka.model.Wound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WoundRepository extends JpaRepository<Wound, Long> {
    @Query
    List<Wound> findByCharacterId(Long characterId);
}
