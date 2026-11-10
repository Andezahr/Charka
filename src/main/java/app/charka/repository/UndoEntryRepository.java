package app.charka.repository;

import app.charka.model.UndoEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UndoEntryRepository extends JpaRepository<UndoEntry, Long> {
    Optional<UndoEntry> findFirstByCampaignIdOrderByIdDesc(Long campaignId);

    List<UndoEntry> findByCampaignIdOrderByIdDesc(Long campaignId);
}
