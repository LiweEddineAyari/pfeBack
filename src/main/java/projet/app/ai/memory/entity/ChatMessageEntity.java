package projet.app.ai.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One chat message: USER prompt, AI assistant reply, or TOOL_EXECUTION audit record.
 * Persisted under {@code ai.chat_messages}, ordered per session by {@code sequence_no}.
 *
 * <p>The {@code tool_input} / {@code tool_output} JSONB columns are kept as raw strings
 * here to keep the entity simple — the AI module never needs to query inside them, only
 * persist them for audit / replay.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_messages", schema = "ai")
public class ChatMessageEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "session_id", nullable = false, columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "role", nullable = false, length = 20)
    private String role; // USER | AI | TOOL_EXECUTION | SYSTEM

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "prompt_text", columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_input", columnDefinition = "jsonb")
    private String toolInput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_output", columnDefinition = "jsonb")
    private String toolOutput;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "sequence_no", nullable = false)
    private Long sequenceNo;

    @Column(name = "created_at")
    private Instant createdAt;
}
