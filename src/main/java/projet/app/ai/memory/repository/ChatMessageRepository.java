package projet.app.ai.memory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import projet.app.ai.memory.entity.ChatMessageEntity;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ChatMessageEntity}.
 * Complex operations that require raw SQL (and would conflict with the
 * project-wide jsqlparser:4.9 version) live in {@link ChatMessageRepositoryImpl}.
 */
public interface ChatMessageRepository
        extends JpaRepository<ChatMessageEntity, UUID>, ChatMessageRepositoryCustom {

    List<ChatMessageEntity> findBySessionIdOrderBySequenceNoAsc(UUID sessionId);

    Page<ChatMessageEntity> findBySessionIdOrderBySequenceNoAsc(UUID sessionId, Pageable pageable);

    long countBySessionId(UUID sessionId);

    @Query("SELECT COALESCE(MAX(m.sequenceNo), 0) FROM ChatMessageEntity m WHERE m.sessionId = :sid")
    long maxSequenceNo(@Param("sid") UUID sessionId);

    @Modifying
    @Query("DELETE FROM ChatMessageEntity m WHERE m.sessionId = :sid")
    int deleteBySessionId(@Param("sid") UUID sessionId);
}
