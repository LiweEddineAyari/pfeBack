package projet.app.ai.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.ai.agent.intent.QueryIntent;
import projet.app.ai.agent.intent.QueryIntentExtractor;
import projet.app.ai.memory.entity.ExtractedEntityEntity;
import projet.app.ai.memory.repository.ExtractedEntityRepository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persists structured financial entities (ratio codes, parameter codes, dates,
 * domains) mentioned in user prompts and AI replies to {@code ai.extracted_entities}.
 * Useful for analytics, replay, and "topics raised in session X" reporting.
 *
 * <p>Reuses the same deterministic {@link QueryIntentExtractor} as the RAG layer
 * so we never need an extra LLM call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityExtractorService {

    private final QueryIntentExtractor intentExtractor;
    private final ExtractedEntityRepository repository;

    @Async
    @Transactional
    public void extractAndPersist(UUID sessionId, String text) {
        if (sessionId == null || text == null || text.isBlank()) {
            return;
        }
        try {
            QueryIntent intent = intentExtractor.extract(text);
            Instant now = Instant.now();
            persistAll(sessionId, "RATIO_CODE", intent.ratioCodes(), now);
            persistAll(sessionId, "PARAMETER_CODE", intent.parameterCodes(), now);
            persistAll(sessionId, "DATE", intent.dates(), now);
            if (intent.domain() != null) {
                persistAll(sessionId, "DOMAIN", java.util.List.of(intent.domain()), now);
            }
        } catch (RuntimeException ex) {
            log.debug("Entity extraction failed for session {}: {}", sessionId, ex.getMessage());
        }
    }

    private void persistAll(UUID sessionId, String type,
                            java.util.Collection<String> values, Instant now) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<String> deduped = new LinkedHashSet<>(values);
        for (String value : deduped) {
            if (value == null || value.isBlank()) continue;
            repository.save(ExtractedEntityEntity.builder()
                    .id(UUID.randomUUID())
                    .sessionId(sessionId)
                    .entityType(type)
                    .entityValue(value)
                    .createdAt(now)
                    .build());
        }
    }
}
