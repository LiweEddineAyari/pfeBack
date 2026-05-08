package projet.app.ai.rag.controller;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import projet.app.ai.rag.ingestion.IngestionReport;
import projet.app.ai.rag.ingestion.RagIngestionService;
import projet.app.ai.rag.ingestion.RagSeedDataService;
import projet.app.ai.rag.ingestion.SeedReport;
import projet.app.ai.rag.repository.RagDocumentRepository;
import projet.app.ai.rag.retrieval.HybridFinancialRetriever;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for the RAG knowledge base.
 *
 * <ul>
 *   <li>{@code POST /ai/rag/ingest}      — upload an Excel knowledge file</li>
 *   <li>{@code GET  /ai/rag/search}      — test hybrid retrieval</li>
 *   <li>{@code POST /ai/rag/seed}        — seed from hard-coded BFI knowledge (idempotent)</li>
 *   <li>{@code POST /ai/rag/seed/force}  — wipe source documents then re-seed</li>
 *   <li>{@code GET  /ai/rag/seed/status} — document counts by type / source</li>
 *   <li>{@code DELETE /ai/rag/source/{source}} — delete all documents for a source</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/ai/rag")
@RequiredArgsConstructor
public class RagAdminController {

    private final RagIngestionService    ingestionService;
    private final RagSeedDataService     seedDataService;
    private final RagDocumentRepository  repository;
    private final HybridFinancialRetriever retriever;

    // ─── Existing upload / search ──────────────────────────────────────────────

    @PostMapping("/ingest")
    public ResponseEntity<IngestionReport> ingest(@RequestParam("file") MultipartFile file) {
        log.info("[RagAdmin] Ingest request — file='{}', size={}",
                file.getOriginalFilename(), file.getSize());
        return ResponseEntity.ok(ingestionService.ingest(file));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, String>>> search(@RequestParam("q") String query) {
        List<Content> results = retriever.retrieve(Query.from(query));
        List<Map<String, String>> shaped = results.stream()
                .map(c -> Map.of("text", c.textSegment().text()))
                .toList();
        return ResponseEntity.ok(shaped);
    }

    // ─── Seed endpoints ────────────────────────────────────────────────────────

    /**
     * Seeds the RAG knowledge base from the hard-coded BFI appetite grid knowledge.
     * Idempotent: returns {@code 200 SKIPPED} if documents for the source already exist.
     */
    @PostMapping("/seed")
    public ResponseEntity<SeedReport> seed() {
        log.info("[RagAdmin] Seed request received (source='{}')", RagSeedDataService.SOURCE);
        SeedReport report = seedDataService.seed();
        HttpStatus status = "SUCCESS".equals(report.status()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(report);
    }

    /**
     * Force re-seed: deletes all documents for the BFI source, then re-seeds from scratch.
     * Use when the knowledge base needs to be refreshed with updated chunk content.
     */
    @PostMapping("/seed/force")
    @Transactional
    public ResponseEntity<SeedReport> forceSeed() {
        log.warn("[RagAdmin] Force re-seed requested — deleting source '{}'", RagSeedDataService.SOURCE);
        int deleted = repository.deleteBySource(RagSeedDataService.SOURCE);
        log.info("[RagAdmin] Deleted {} documents for source '{}'", deleted, RagSeedDataService.SOURCE);
        SeedReport report = seedDataService.seed();
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    /**
     * Returns document counts by source and document type for verification.
     */
    @GetMapping("/seed/status")
    public ResponseEntity<Map<String, Object>> seedStatus() {
        long total  = repository.count();
        long seeded = repository.countBySource(RagSeedDataService.SOURCE);
        return ResponseEntity.ok(Map.of(
                "totalDocuments",      total,
                "seededByBfiSource",   seeded,
                "otherDocuments",      total - seeded,
                "documentTypeCounts",  Map.of(
                        "PARAMETER_DEFINITION",    repository.countByDocumentType("PARAMETER_DEFINITION"),
                        "RATIO_DEFINITION",        repository.countByDocumentType("RATIO_DEFINITION"),
                        "THRESHOLD_INTERPRETATION",repository.countByDocumentType("THRESHOLD_INTERPRETATION"),
                        "RECOMMENDATION",          repository.countByDocumentType("RECOMMENDATION"),
                        "RISK_INTERPRETATION",     repository.countByDocumentType("RISK_INTERPRETATION"),
                        "RATIO_RELATIONSHIP",      repository.countByDocumentType("RATIO_RELATIONSHIP"),
                        "REGULATION",              repository.countByDocumentType("REGULATION")
                )
        ));
    }

    /**
     * Deletes all RAG documents belonging to a specific ingestion source.
     * Useful for cleaning up after an Excel ingestion that produced bad data.
     */
    @DeleteMapping("/source/{source}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteBySource(@PathVariable String source) {
        log.warn("[RagAdmin] Deleting all documents for source '{}'", source);
        int deleted = repository.deleteBySource(source);
        return ResponseEntity.ok(Map.of(
                "source",  source,
                "deleted", deleted
        ));
    }
}
