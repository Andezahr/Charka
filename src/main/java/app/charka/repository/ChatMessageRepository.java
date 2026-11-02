package app.charka.repository;

import app.charka.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByCampaignIdOrderByCreatedAtDescIdDesc(Long campaignId, Pageable page);
}
