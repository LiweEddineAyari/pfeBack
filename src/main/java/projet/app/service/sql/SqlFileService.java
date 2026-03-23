package projet.app.service.sql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for executing SQL files against the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlFileService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Execute SQL statements from a file.
     * Returns the total number of rows affected.
     */
    public int executeSqlFile(Path filePath) {
        log.info("Executing SQL file: {}", filePath);
        
        try {
            String content = Files.readString(filePath);
            List<String> statements = parseSqlStatements(content);
            
            log.info("Found {} SQL statements to execute", statements.size());
            
            int totalRowsAffected = 0;
            for (String statement : statements) {
                if (!statement.isBlank()) {
                    try {
                        int rowsAffected = jdbcTemplate.update(statement);
                        totalRowsAffected += rowsAffected;
                        log.debug("Executed statement, {} rows affected", rowsAffected);
                    } catch (Exception e) {
                        log.error("Error executing SQL: {}", statement.substring(0, Math.min(100, statement.length())));
                        throw e;
                    }
                }
            }
            
            log.info("SQL file executed successfully. Total rows affected: {}", totalRowsAffected);
            return totalRowsAffected;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to read SQL file: " + filePath, e);
        }
    }

    /**
     * Parse SQL content into individual statements.
     * Splits by semicolon but handles multi-line statements.
     */
    private List<String> parseSqlStatements(String content) {
        // Remove SQL comments
        String cleanedContent = removeComments(content);
        
        // Split by semicolon and filter empty statements
        return Arrays.stream(cleanedContent.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Remove SQL comments (single-line and multi-line)
     */
    private String removeComments(String sql) {
        // Remove single-line comments
        sql = sql.replaceAll("--.*?(\\r?\\n|$)", "\n");
        // Remove multi-line comments
        sql = sql.replaceAll("/\\*.*?\\*/", " ");
        return sql;
    }
}
