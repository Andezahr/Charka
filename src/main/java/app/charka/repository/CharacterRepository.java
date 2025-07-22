package app.charka.repository;

import app.charka.model.Campaign;
import app.charka.model.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {
    List<Character> findByCampaign(Campaign campaign);
}