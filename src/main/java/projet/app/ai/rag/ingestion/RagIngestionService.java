package projet.app.ai.rag.ingestion;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the RAG ingestion pipeline:
 *
 * <pre>
 *   MultipartFile → ExcelParser → ChunkingEngine → MetadataEnricher
 *                 → OpenAI embedding (batched) → INSERT into rag.documents
 * </pre>
 *
 * <p>We use a single SQL upsert per chunk that writes both the {@code embedding}
 * column ({@code pgvector}) and the rich metadata columns. This keeps the AI
 * module independent of LangChain4j's internal table layout for embedding stores.
 *
 * <p>If no embedding model is available (no API key), we still persist the
 * chunks with {@code embedding = NULL} — they remain searchable via full-text
 * search and exact-code match, just not semantically.
 */
@Slf4j
@Service
public class RagIngestionService {

    private static final int EMBED_BATCH = 100;

    private final ExcelParser excelParser;
    private final ChunkingEngine chunkingEngine;
    private final MetadataEnricher metadataEnricher;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final JdbcTemplate jdbc;
    private final int dimension;

    public RagIngestionService(ExcelParser excelParser,
                               ChunkingEngine chunkingEngine,
                               MetadataEnricher metadataEnricher,
                               ObjectProvider<EmbeddingModel> embeddingModelProvider,
                               DataSource dataSource,
                               @Value("${ai.rag.embedding-dimension:1536}") int dimension) {
        this.excelParser = excelParser;
        this.chunkingEngine = chunkingEngine;
        this.metadataEnricher = metadataEnricher;
        this.embeddingModelProvider = embeddingModelProvider;
        this.jdbc = new JdbcTemplate(dataSource);
        this.dimension = dimension;
    }

    @Transactional
    public IngestionReport ingest(MultipartFile file) {
        Instant t0 = Instant.now();
        List<String> warnings = new ArrayList<>();

        List<RawRow> rows = excelParser.parse(file);
        List<RagChunk> chunks = chunkingEngine.chunk(rows);
        List<RagChunk> enriched = metadataEnricher.enrich(chunks);

        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            warnings.add("Embedding model unavailable: chunks persisted WITHOUT embeddings.");
            log.warn(warnings.get(warnings.size() - 1));
        }

        int persisted = 0;
        int failed = 0;
        for (int batchStart = 0; batchStart < enriched.size(); batchStart += EMBED_BATCH) {
            int batchEnd = Math.min(batchStart + EMBED_BATCH, enriched.size());
            List<RagChunk> batch = enriched.subList(batchStart, batchEnd);
            try {
                List<Embedding> embeddings = embeddingModel == null
                        ? new ArrayList<>()
                        : embedBatch(embeddingModel, batch);
                for (int i = 0; i < batch.size(); i++) {
                    RagChunk c = batch.get(i);
                    Embedding emb = i < embeddings.size() ? embeddings.get(i) : null;
                    insertChunk(c, emb);
                    persisted++;
                }
            } catch (RuntimeException ex) {
                failed += batch.size();
                warnings.add("Batch %d-%d failed: %s".formatted(batchStart, batchEnd, ex.getMessage()));
                log.warn("Batch ingestion failed: {}", ex.getMessage());
            }
        }

        log.info("Ingestion completed in {} ms (parsed={}, chunks={}, persisted={}, failed={})",
                java.time.Duration.between(t0, Instant.now()).toMillis(),
                rows.size(), enriched.size(), persisted, failed);

        return IngestionReport.builder()
                .rowsParsed(rows.size())
                .chunksProduced(enriched.size())
                .chunksPersisted(persisted)
                .chunksFailed(failed)
                .warnings(warnings)
                .status(failed == 0 ? "OK" : (persisted > 0 ? "PARTIAL" : "FAILED"))
                .completedAt(Instant.now())
                .build();
    }

    private List<Embedding> embedBatch(EmbeddingModel model, List<RagChunk> batch) {
        List<TextSegment> segments = batch.stream()
                .map(c -> TextSegment.from(c.text()))
                .toList();
        return model.embedAll(segments).content();
    }

    private void insertChunk(RagChunk chunk, Embedding embedding) {
        UUID id = UUID.randomUUID();
        boolean withEmbedding = hasEmbeddingColumn();
        String vectorLiteral = (!withEmbedding || embedding == null)
                ? null
                : toPgVectorLiteral(embedding.vector(), dimension);

        String sql = withEmbedding
                ? """
                  INSERT INTO rag.documents (
                      id, title, content, embedding, document_type, category,
                      ratio_code, parameter_code, ratio_family, domain,
                      regulation, source, keywords, language, created_at
                  ) VALUES (?, ?, ?, ?::vector, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                  """
                : """
                  INSERT INTO rag.documents (
                      id, title, content, document_type, category,
                      ratio_code, parameter_code, ratio_family, domain,
                      regulation, source, keywords, language, created_at
                  ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                  """;

        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            int p = 1;
            ps.setObject(p++, id);
            ps.setString(p++, chunk.title());
            ps.setString(p++, chunk.text());
            if (withEmbedding) {
                if (vectorLiteral == null) ps.setNull(p++, Types.OTHER);
                else                       ps.setString(p++, vectorLiteral);
            }
            setNullable(ps, p++, chunk.documentType());
            setNullable(ps, p++, chunk.category());
            setNullable(ps, p++, chunk.ratioCode());
            setNullable(ps, p++, chunk.parameterCode());
            setNullable(ps, p++, chunk.ratioFamily());
            setNullable(ps, p++, chunk.domain());
            setNullable(ps, p++, chunk.regulation());
            setNullable(ps, p++, chunk.source());
            setTextArray(ps, p++, con, chunk.keywords());
            setNullable(ps, p++, chunk.language() == null ? "fr" : chunk.language());
            ps.setTimestamp(p, Timestamp.from(Instant.now()));
            return ps;
        });
    }

    private static void setNullable(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.VARCHAR);
        else           ps.setString(idx, v);
    }

    private static void setTextArray(PreparedStatement ps, int idx,
                                     Connection con, String[] values) throws SQLException {
        if (values == null || values.length == 0) {
            ps.setNull(idx, Types.ARRAY);
            return;
        }
        Array arr = con.createArrayOf("text", values);
        ps.setArray(idx, arr);
    }

    private Boolean embeddingColumnExists;

    private boolean hasEmbeddingColumn() {
        if (embeddingColumnExists == null) {
            try {
                Integer c = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema='rag' AND table_name='documents'
                          AND column_name='embedding'
                        """, Integer.class);
                embeddingColumnExists = c != null && c > 0;
            } catch (RuntimeException ex) {
                embeddingColumnExists = false;
            }
        }
        return embeddingColumnExists;
    }

    private static String toPgVectorLiteral(float[] vec, int expectedDim) {
        if (vec.length != expectedDim) {
            throw new IllegalStateException(
                    "Embedding dimension mismatch: got " + vec.length + ", expected " + expectedDim);
        }
        StringBuilder sb = new StringBuilder(vec.length * 6);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
