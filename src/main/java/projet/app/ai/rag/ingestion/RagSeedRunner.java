package projet.app.ai.rag.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Startup hook that triggers {@link RagSeedDataService#seed()} on first boot.
 * The seed is idempotent — if {@code rag.documents} already contains documents
 * for the BFI source, this is a no-op.
 *
 * <p>Set {@code ai.rag.seed-on-startup=false} in {@code application.properties}
 * to disable automatic seeding (useful in production when the knowledge base
 * is loaded via {@code POST /ai/rag/seed} or {@code POST /ai/rag/ingest}).
 */
@Slf4j
@Component
public class RagSeedRunner implements CommandLineRunner {

    private final RagSeedDataService seedDataService;
    private final boolean            enabled;

    public RagSeedRunner(RagSeedDataService seedDataService,
                         @Value("${ai.rag.seed-on-startup:true}") boolean enabled) {
        this.seedDataService = seedDataService;
        this.enabled         = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("[RagSeedRunner] Startup seeding disabled (ai.rag.seed-on-startup=false).");
            return;
        }
        log.info("[RagSeedRunner] Starting RAG knowledge base seeding...");
        SeedReport report = seedDataService.seed();
        log.info("[RagSeedRunner] {} — {}", report.status(), report.message());
    }
}
