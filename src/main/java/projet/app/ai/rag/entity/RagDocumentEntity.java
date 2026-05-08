package projet.app.ai.rag.entity;

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
 * One row of the RAG knowledge base. The {@code embedding} column is mapped as
 * {@code Object} because PostgreSQL {@code vector(N)} is a custom type — ingestion
 * is delegated to the {@code PgVectorEmbeddingStore} bean (which uses raw JDBC),
 * so Hibernate never reads or writes that column directly. We expose
 * non-embedding metadata for retrieval-time filtering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "documents", schema = "rag")
public class RagDocumentEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "document_type", length = 50)
    private String documentType;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "ratio_code", length = 50)
    private String ratioCode;

    @Column(name = "parameter_code", length = 50)
    private String parameterCode;

    @Column(name = "ratio_family", length = 100)
    private String ratioFamily;

    @Column(name = "domain", length = 100)
    private String domain;

    @Column(name = "regulation", length = 100)
    private String regulation;

    @Column(name = "source", length = 200)
    private String source;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "keywords", columnDefinition = "text[]")
    private String[] keywords;

    @Column(name = "language", length = 10)
    private String language;

    @Column(name = "created_at")
    private Instant createdAt;
}
