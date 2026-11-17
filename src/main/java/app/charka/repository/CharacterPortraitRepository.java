package app.charka.repository;

import app.charka.model.CharacterPortrait;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CharacterPortraitRepository extends JpaRepository<CharacterPortrait, Long> {

    /** Версии портретов персонажей кампании — без байтов картинок */
    @Query("select p.characterId as characterId, p.updatedAt as updatedAt from CharacterPortrait p, Character c"
            + " where c.id = p.characterId and c.campaign.id = :campaignId")
    List<Version> versions(@Param("campaignId") Long campaignId);

    interface Version {
        Long getCharacterId();

        LocalDateTime getUpdatedAt();
    }
}
