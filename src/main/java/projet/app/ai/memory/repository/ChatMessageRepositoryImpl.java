package projet.app.ai.memory.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * JdbcTemplate-based implementation of {@link ChatMessageRepositoryCustom}.
 * Uses raw SQL to avoid Spring Data JPA's native-query parsing (which requires
 * jsqlparser ≤ 4.7 and conflicts with the project-wide jsqlparser:4.9 version
 * needed by the existing ETL formula engine).
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepositoryCustom {

    private final DataSource dataSource;

    @Override
    public List<String> findSessionsNeedingSummary(int threshold) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String sql = """
                SELECT m.session_id::text
                FROM ai.chat_messages m
                LEFT JOIN (
                    SELECT session_id, MAX(created_at) AS last_summary_at
                    FROM ai.chat_summaries
                    GROUP BY session_id
                ) s ON s.session_id = m.session_id
                WHERE m.created_at > COALESCE(s.last_summary_at, '-infinity'::timestamp)
                GROUP BY m.session_id
                HAVING COUNT(*) >= ?
                """;
        return jdbc.queryForList(sql, String.class, threshold);
    }

    @Override
    public int trimToLastN(UUID sessionId, int keep) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String sql = """
                DELETE FROM ai.chat_messages
                WHERE session_id = ?
                  AND id NOT IN (
                      SELECT id
                      FROM ai.chat_messages
                      WHERE session_id = ?
                      ORDER BY sequence_no DESC
                      LIMIT ?
                  )
                """;
        return jdbc.update(sql, sessionId, sessionId, keep);
    }
}
