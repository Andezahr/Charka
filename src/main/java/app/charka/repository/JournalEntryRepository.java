package app.charka.repository;

import app.charka.model.JournalEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByCampaignIdOrderByCreatedAtDescIdDesc(Long campaignId, Pageable pageable);

    List<JournalEntry> findByCampaignIdAndHiddenFalseOrderByCreatedAtDescIdDesc(Long campaignId, Pageable pageable);
}
