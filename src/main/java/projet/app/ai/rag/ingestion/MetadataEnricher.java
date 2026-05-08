package projet.app.ai.rag.ingestion;

import org.springframework.stereotype.Component;
import projet.app.ai.agent.intent.QueryIntent;
import projet.app.ai.agent.intent.QueryIntentExtractor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Post-processes a list of {@link RagChunk}s to fill in missing metadata fields
 * by re-using the same deterministic rules as {@link QueryIntentExtractor}:
 * extracts ratio / parameter codes from the chunk body when not declared, and
 * derives the {@code domain} field when blank.
 */
@Component
public class MetadataEnricher {

    private final QueryIntentExtractor intentExtractor;

    public MetadataEnricher(QueryIntentExtractor intentExtractor) {
        this.intentExtractor = intentExtractor;
    }

    public List<RagChunk> enrich(List<RagChunk> chunks) {
        return chunks.stream().map(this::enrichOne).toList();
    }

    private RagChunk enrichOne(RagChunk chunk) {
        String ratioCode = chunk.ratioCode();
        String parameterCode = chunk.parameterCode();
        String domain = chunk.domain();
        String[] keywords = chunk.keywords();

        if (ratioCode == null || parameterCode == null || domain == null
                || keywords == null || keywords.length == 0) {
            QueryIntent intent = intentExtractor.extract(chunk.title() + ". " + chunk.text());
            if (ratioCode == null && !intent.ratioCodes().isEmpty()) {
                ratioCode = intent.ratioCodes().get(0);
            }
            if (parameterCode == null && !intent.parameterCodes().isEmpty()) {
                parameterCode = intent.parameterCodes().get(0);
            }
            if (domain == null && intent.domain() != null) {
                domain = intent.domain();
            }
            if (keywords == null || keywords.length == 0) {
                Set<String> kw = new LinkedHashSet<>(intent.keywords());
                if (ratioCode != null) kw.add(ratioCode);
                if (parameterCode != null) kw.add(parameterCode);
                keywords = kw.stream().limit(20).toArray(String[]::new);
            } else {
                Set<String> merged = new LinkedHashSet<>(Arrays.asList(keywords));
                merged.addAll(intent.keywords());
                if (ratioCode != null) merged.add(ratioCode);
                if (parameterCode != null) merged.add(parameterCode);
                keywords = merged.stream().limit(30).toArray(String[]::new);
            }
        }

        return RagChunk.builder()
                .title(chunk.title())
                .text(chunk.text())
                .documentType(chunk.documentType())
                .category(chunk.category())
                .ratioCode(ratioCode)
                .parameterCode(parameterCode)
                .ratioFamily(chunk.ratioFamily())
                .domain(domain)
                .regulation(chunk.regulation())
                .source(chunk.source())
                .keywords(keywords)
                .language(chunk.language())
                .build();
    }
}
