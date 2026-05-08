package projet.app.ai.memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import projet.app.ai.memory.entity.ChatSummaryEntity;

import java.util.List;
import java.util.UUID;

public interface ChatSummaryRepository extends JpaRepository<ChatSummaryEntity, UUID> {

    List<ChatSummaryEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
