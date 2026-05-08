package projet.app.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Programmatic initializer that runs AFTER Spring has already executed
 * {@code schema.sql}. It attempts to:
 *
 * <ol>
 *   <li>Install the {@code pgvector} PostgreSQL extension.</li>
 *   <li>Create {@code rag.documents} with an {@code embedding VECTOR(1536)} column
 *       and its associated ivfflat + FTS + metadata indexes.</li>
 * </ol>
 *
 * <p><b>If pgvector is not installed on the server</b>, step 1 fails silently
 * (a WARN is logged), step 2 then creates {@code rag.documents} <em>without</em>
 * the {@code embedding} column. The rest of the application — including all
 * existing ETL endpoints — continues to work normally. The RAG layer degrades to
 * FTS + exact-code retrieval only; semantic search becomes available the moment
 * pgvector is installed and the app is restarted.
 *
 * <p>All DDL statements are idempotent ({@code IF NOT EXISTS /
 * ADD COLUMN IF NOT EXISTS}), so restarting the app after pgvector is installed
 * automatically adds the missing column and indexes without data loss.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSchemaInitializer {

    private final DataSource dataSource;

    @PostConstruct
    public void initVectorSchema() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        boolean vectorAvailable = tryEnableVector(jdbc);
        createRagDocumentsTable(jdbc, vectorAvailable);
        if (vectorAvailable) {
            createVectorIndex(jdbc);
        }
        createMetadataIndexes(jdbc);
        log.info("AiSchemaInitializer complete — pgvector={}, semantic search={}",
                vectorAvailable, vectorAvailable ? "ENABLED" : "DISABLED (FTS-only)");
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private boolean tryEnableVector(JdbcTemplate jdbc) {
        try {
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
            log.info("pgvector extension is available — semantic RAG ENABLED.");
            return true;
        } catch (Exception ex) {
            log.warn("pgvector extension is NOT installed on this PostgreSQL server. " +
                    "Semantic (embedding) search is DISABLED. " +
                    "To enable it: install pgvector and restart the application. " +
                    "Error: {}", ex.getMessage());
            return false;
        }
    }

    private void createRagDocumentsTable(JdbcTemplate jdbc, boolean vectorAvailable) {
        // Base table without the vector column — works with or without pgvector.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS rag.documents (
                    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    title           TEXT NOT NULL,
                    content         TEXT NOT NULL,
                    document_type   VARCHAR(50),
                    category        VARCHAR(100),
                    ratio_code      VARCHAR(50),
                    parameter_code  VARCHAR(50),
                    ratio_family    VARCHAR(100),
                    domain          VARCHAR(100),
                    regulation      VARCHAR(100),
                    source          VARCHAR(200),
                    keywords        TEXT[],
                    language        VARCHAR(10) DEFAULT 'fr',
                    created_at      TIMESTAMP DEFAULT now()
                )
                """);

        if (vectorAvailable) {
            // Add the embedding column only when pgvector is present.
            // ADD COLUMN IF NOT EXISTS is idempotent on PostgreSQL 9.6+.
            try {
                jdbc.execute(
                        "ALTER TABLE rag.documents ADD COLUMN IF NOT EXISTS embedding VECTOR(1536)");
            } catch (Exception ex) {
                log.warn("Could not add embedding column to rag.documents: {}", ex.getMessage());
            }
        }
    }

    private void createVectorIndex(JdbcTemplate jdbc) {
        // ivfflat requires the table to have at least one row OR lists ≤ 0 to build.
        // We use a try/catch so a fresh (empty) table doesn't crash startup.
        try {
            jdbc.execute("""
                    CREATE INDEX IF NOT EXISTS idx_rag_embedding
                        ON rag.documents USING ivfflat (embedding vector_cosine_ops)
                        WITH (lists = 100)
                    """);
        } catch (Exception ex) {
            log.debug("Vector index creation deferred (table may be empty): {}", ex.getMessage());
        }
    }

    private void createMetadataIndexes(JdbcTemplate jdbc) {
        String[] idxStatements = {
                "CREATE INDEX IF NOT EXISTS idx_rag_ratio_code ON rag.documents (ratio_code)",
                "CREATE INDEX IF NOT EXISTS idx_rag_param_code ON rag.documents (parameter_code)",
                "CREATE INDEX IF NOT EXISTS idx_rag_doc_type   ON rag.documents (document_type)",
                "CREATE INDEX IF NOT EXISTS idx_rag_domain     ON rag.documents (domain)",
                """
                CREATE INDEX IF NOT EXISTS idx_rag_fts ON rag.documents
                    USING gin(to_tsvector('french',
                        coalesce(content,'') || ' ' || coalesce(title,'')))
                """
        };
        for (String sql : idxStatements) {
            try {
                jdbc.execute(sql);
            } catch (Exception ex) {
                log.warn("Could not create RAG index: {}", ex.getMessage());
            }
        }
    }
}
