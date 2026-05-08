package projet.app.ai.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_summaries", schema = "ai")
public class ChatSummaryEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "session_id", nullable = false, columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "turn_count")
    private Integer turnCount;

    @Column(name = "created_at")
    private Instant createdAt;
}
