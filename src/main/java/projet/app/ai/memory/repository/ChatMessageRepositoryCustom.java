package projet.app.ai.memory.repository;

import java.util.List;
import java.util.UUID;

/**
 * Supplementary interface for complex ChatMessage operations that use raw JDBC
 * so we avoid Spring Data JPA's native-query JSQLParser path (which is
 * incompatible with the project-level jsqlparser:4.9 override).
 */
public interface ChatMessageRepositoryCustom {

    /**
     * Find session IDs that have accumulated {@code threshold} or more messages
     * since the last summary was created for that session.
     */
    List<String> findSessionsNeedingSummary(int threshold);

    /**
     * Delete all but the most-recent {@code keep} messages (by sequence_no)
     * in the given session. Returns the number of rows deleted.
     */
    int trimToLastN(UUID sessionId, int keep);
}
