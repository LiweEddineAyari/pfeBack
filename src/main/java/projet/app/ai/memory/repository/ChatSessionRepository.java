package projet.app.ai.memory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import projet.app.ai.memory.entity.ChatSessionEntity;

import java.time.Instant;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {

    Page<ChatSessionEntity> findByUserIdAndStatusOrderByLastMessageAtDesc(
            String userId, String status, Pageable pageable);

    @Modifying
    @Query("update ChatSessionEntity s set s.title = :title, s.updatedAt = :now where s.id = :id")
    int updateTitle(@Param("id") UUID id,
                    @Param("title") String title,
                    @Param("now") Instant now);

    @Modifying
    @Query("update ChatSessionEntity s set s.lastMessageAt = :now, s.updatedAt = :now where s.id = :id")
    int touch(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update ChatSessionEntity s set s.status = 'ARCHIVED', s.updatedAt = :now where s.id = :id")
    int archive(@Param("id") UUID id, @Param("now") Instant now);
}
