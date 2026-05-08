package projet.app.ai.rag.ingestion;

import lombok.Builder;

/**
 * One ingestion-ready chunk (one concept = one chunk). Carries all metadata
 * needed by {@code MetadataEnricher} and the retrieval-time filters.
 */
@Builder
public record RagChunk(
        String title,
        String text,
        String documentType,
        String category,
        String ratioCode,
        String parameterCode,
        String ratioFamily,
        String domain,
        String regulation,
        String source,
        String[] keywords,
        String language
) {}
