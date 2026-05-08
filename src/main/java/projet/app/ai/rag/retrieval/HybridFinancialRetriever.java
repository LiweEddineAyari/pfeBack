package projet.app.ai.rag.retrieval;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import projet.app.ai.agent.intent.QueryIntent;
import projet.app.ai.agent.intent.QueryIntentExtractor;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hybrid retriever that fuses three independent ranking strategies and returns
 * the top-K results to the LangChain4j {@code RetrievalAugmentor}:
 *
 * <ol>
 *   <li><b>Semantic</b>  — pgvector cosine similarity against query embedding</li>
 *   <li><b>FTS</b>       — PostgreSQL full-text search ({@code french} dictionary)</li>
 *   <li><b>Exact code</b> — boost for chunks whose {@code ratio_code} or
 *                            {@code parameter_code} appears verbatim in the query</li>
 * </ol>
 *
 * <p>Fusion is done via Reciprocal Rank Fusion (RRF) with constant {@code k=60}.
 * If the embedding model bean is unavailable (no API key configured), we
 * degrade gracefully to FTS-only — the chat module still works, just without
 * semantic recall.
 */
@Slf4j
@Component
public class HybridFinancialRetriever implements ContentRetriever {

    private final JdbcTemplate jdbc;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final QueryIntentExtractor intentExtractor;

    private final int topK;
    private final int rrfK;
    private volatile Boolean embeddingColumnExists; // null = unchecked, lazy-init on first call

    public HybridFinancialRetriever(DataSource dataSource,
                                    ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                    QueryIntentExtractor intentExtractor,
                                    @Value("${ai.rag.top-k:8}") int topK,
                                    @Value("${ai.rag.rrf-k:60}") int rrfK) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.embeddingModelProvider = embeddingModelProvider;
        this.intentExtractor = intentExtractor;
        this.topK = topK;
        this.rrfK = rrfK;
    }

    @Override
    public List<Content> retrieve(Query query) {
        String text = query.text();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        QueryIntent intent = intentExtractor.extract(text);

        List<RagHit> semantic = safeSemanticSearch(text, intent, topK * 2);
        List<RagHit> fts = safeFtsSearch(intent, topK * 2);
        List<RagHit> exact = safeExactSearch(intent, topK);

        List<RagHit> fused = rrfFuse(List.of(semantic, fts, exact), topK);

        List<Content> contents = new ArrayList<>(fused.size());
        for (RagHit hit : fused) {
            String header = "[%s | %s | %s]".formatted(
                    nullToEmpty(hit.documentType),
                    nullToEmpty(hit.ratioCode),
                    nullToEmpty(hit.domain));
            contents.add(Content.from(TextSegment.from(
                    header + "\n" + hit.title + "\n\n" + hit.content)));
        }
        if (log.isDebugEnabled()) {
            log.debug("RAG retrieve: query='{}' intent={} sem={} fts={} exact={} fused={}",
                    text, intent.intentType(), semantic.size(), fts.size(),
                    exact.size(), fused.size());
        }
        return contents;
    }

    // ─── per-strategy queries (fail-soft: each returns [] on error) ─────────────

    private List<RagHit> safeSemanticSearch(String text, QueryIntent intent, int limit) {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null || !hasEmbeddingColumn()) {
            return List.of();
        }
        try {
            Embedding embedding = model.embed(text).content();
            String vector = toPgVectorLiteral(embedding.vector());
            return executeSemanticQuery(vector, intent, limit);
        } catch (RuntimeException ex) {
            log.warn("Semantic search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Lazily checks (and caches) whether {@code rag.documents.embedding} exists.
     * When pgvector is not installed the column is omitted by
     * {@code AiSchemaInitializer}, and we must skip the semantic SQL entirely
     * to avoid a "bad SQL grammar" warning on every chat turn.
     */
    private boolean hasEmbeddingColumn() {
        Boolean cached = embeddingColumnExists;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (embeddingColumnExists != null) {
                return embeddingColumnExists;
            }
            try {
                Integer count = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'rag'
                          AND table_name   = 'documents'
                          AND column_name  = 'embedding'
                        """, Integer.class);
                boolean present = count != null && count > 0;
                embeddingColumnExists = present;
                if (!present) {
                    log.info("Semantic search disabled: rag.documents.embedding column is absent (pgvector not installed).");
                }
                return present;
            } catch (RuntimeException ex) {
                embeddingColumnExists = false;
                log.warn("Could not inspect rag.documents schema; disabling semantic search: {}", ex.getMessage());
                return false;
            }
        }
    }

    private List<RagHit> executeSemanticQuery(String vectorLiteral, QueryIntent intent, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, title, content, document_type, ratio_code, parameter_code,
                       domain, ratio_family, regulation,
                       1 - (embedding <=> ?::vector) AS score
                FROM rag.documents
                WHERE embedding IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        args.add(vectorLiteral);

        if (intent.domain() != null) {
            sql.append(" AND (domain = ? OR domain IS NULL)");
            args.add(intent.domain());
        }
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        args.add(vectorLiteral);
        args.add(limit);

        return jdbc.query(sql.toString(), HIT_MAPPER, args.toArray());
    }

    private List<RagHit> safeFtsSearch(QueryIntent intent, int limit) {
        if (intent.keywords().isEmpty()) {
            return List.of();
        }
        try {
            String tsquery = String.join(" | ", intent.keywords());
            String sql = """
                    SELECT id, title, content, document_type, ratio_code, parameter_code,
                           domain, ratio_family, regulation,
                           ts_rank(
                               to_tsvector('french', coalesce(content,'') || ' ' || coalesce(title,'')),
                               to_tsquery('french', ?)
                           ) AS score
                    FROM rag.documents
                    WHERE to_tsvector('french', coalesce(content,'') || ' ' || coalesce(title,''))
                          @@ to_tsquery('french', ?)
                    ORDER BY score DESC
                    LIMIT ?
                    """;
            return jdbc.query(sql, HIT_MAPPER, tsquery, tsquery, limit);
        } catch (RuntimeException ex) {
            log.warn("FTS search failed (often: invalid tsquery): {}", ex.getMessage());
            return List.of();
        }
    }

    private List<RagHit> safeExactSearch(QueryIntent intent, int limit) {
        if (intent.ratioCodes().isEmpty() && intent.parameterCodes().isEmpty()) {
            return List.of();
        }
        try {
            StringBuilder sql = new StringBuilder("""
                    SELECT id, title, content, document_type, ratio_code, parameter_code,
                           domain, ratio_family, regulation, 1.0 AS score
                    FROM rag.documents
                    WHERE 1=0
                    """);
            List<Object> args = new ArrayList<>();
            if (!intent.ratioCodes().isEmpty()) {
                sql.append(" OR ratio_code IN (")
                        .append(",".repeat(intent.ratioCodes().size() - 1).replaceAll(",", "?,"))
                        .append("?)");
                args.addAll(intent.ratioCodes());
            }
            if (!intent.parameterCodes().isEmpty()) {
                sql.append(" OR parameter_code IN (")
                        .append(",".repeat(intent.parameterCodes().size() - 1).replaceAll(",", "?,"))
                        .append("?)");
                args.addAll(intent.parameterCodes());
            }
            sql.append(" LIMIT ?");
            args.add(limit);
            return jdbc.query(sql.toString(), HIT_MAPPER, args.toArray());
        } catch (RuntimeException ex) {
            log.warn("Exact-code search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    // ─── RRF fusion ─────────────────────────────────────────────────────────────

    private List<RagHit> rrfFuse(List<List<RagHit>> rankedLists, int topK) {
        Map<UUID, Double> rrfScores = new HashMap<>();
        Map<UUID, RagHit> dedup = new LinkedHashMap<>();
        for (List<RagHit> list : rankedLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                RagHit hit = list.get(rank);
                rrfScores.merge(hit.id, 1.0 / (rrfK + rank + 1), Double::sum);
                dedup.putIfAbsent(hit.id, hit);
            }
        }
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> dedup.get(e.getKey()))
                .toList();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private static String toPgVectorLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder(vec.length * 6);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private record RagHit(
            UUID id,
            String title,
            String content,
            String documentType,
            String ratioCode,
            String parameterCode,
            String domain,
            String ratioFamily,
            String regulation,
            double score
    ) {}

    private static final RowMapper<RagHit> HIT_MAPPER = (rs, n) -> new RagHit(
            (UUID) rs.getObject("id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("document_type"),
            rs.getString("ratio_code"),
            rs.getString("parameter_code"),
            rs.getString("domain"),
            rs.getString("ratio_family"),
            rs.getString("regulation"),
            rs.getDouble("score")
    );

    /**
     * Marker referenced only to keep the {@link PreparedStatement} import alive
     * (used implicitly by Spring's JdbcTemplate parameter binding).
     */
    @SuppressWarnings("unused")
    private static final Class<?> PS_REF = PreparedStatement.class;
}
