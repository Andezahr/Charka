package app.charka.repository;

import app.charka.model.Campaign;
import app.charka.model.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {
     Optional<List<Character>> findByCampaign(Campaign campaign);
}