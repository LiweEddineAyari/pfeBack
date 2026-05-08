package projet.app.ai.rag.ingestion;

import java.time.Instant;

/**
 * Outcome of a {@link RagSeedDataService#seed()} call.
 */
public record SeedReport(
        String  status,
        long    chunksInserted,
        long    existingChunks,
        String  message,
        Instant completedAt
) {
    public static SeedReport success(long count) {
        return new SeedReport("SUCCESS", count, 0,
                count + " RAG knowledge chunks seeded successfully.",
                Instant.now());
    }

    public static SeedReport skipped(long existing) {
        return new SeedReport("SKIPPED", 0, existing,
                "Seed skipped — " + existing + " documents already present for source "
                        + RagSeedDataService.SOURCE + ".",
                Instant.now());
    }

    public static SeedReport partial(long inserted, long failed) {
        return new SeedReport("PARTIAL", inserted, 0,
                inserted + " chunks seeded, " + failed + " failed.",
                Instant.now());
    }
}
