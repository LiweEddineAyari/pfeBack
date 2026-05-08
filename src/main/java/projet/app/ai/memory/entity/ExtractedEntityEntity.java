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
@Table(name = "extracted_entities", schema = "ai")
public class ExtractedEntityEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "session_id", columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_value", length = 200)
    private String entityValue;

    @Column(name = "created_at")
    private Instant createdAt;
}
