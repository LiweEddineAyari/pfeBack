package projet.app.service.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import projet.app.dto.ExcelRowDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for reading SQL INSERT files into ExcelRowDto format.
 * Parses SQL INSERT statements to extract column-value pairs
 * so that column mapping can be applied before staging insert.
 */
@Slf4j
@Service
public class SqlReaderService {

    /**
     * Parse SQL INSERT statements into ExcelRowDto rows.
     * Supports:
     *   INSERT INTO [schema.]table (col1, col2) VALUES (v1, v2), (v3, v4);
     *   Multiple INSERT statements in one file.
     *   Multi-row VALUES in a single INSERT.
     */
    public List<ExcelRowDto> readSqlFile(Path filePath, String batchId) throws IOException {
        log.info("Parsing SQL file into rows: {}", filePath);

        String raw = Files.readString(filePath);

        // Strip comments
        String content = raw
                .replace("\r\n", "\n")
                .replaceAll("--[^\n]*", "")
                .replaceAll("/\\*[\\s\\S]*?\\*/", " ")
                .trim();

        List<ExcelRowDto> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();

        // Regex: match INSERT INTO [schema.]table (cols) VALUES ...;
        // Handles schema.table, `schema`.`table`, quoted names
        Pattern insertPattern = Pattern.compile(
                "INSERT\\s+INTO\\s+[\\w`\"\\[\\].]+\\s*" +
                        "\\(([^)]+)\\)\\s*VALUES\\s*(.+?)(?=;|INSERT|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher insertMatcher = insertPattern.matcher(content);
        int rowIndex = 1;

        while (insertMatcher.find()) {
            String colsPart = insertMatcher.group(1);
            String valuesPart = insertMatcher.group(2).trim();

            // Parse column names from this INSERT statement
            List<String> insertCols = Arrays.stream(colsPart.split(","))
                    .map(c -> c.trim().replaceAll("[`\"\\[\\]]", ""))
                    .filter(c -> !c.isEmpty())
                    .collect(Collectors.toList());

            // Use first INSERT's columns as the schema reference
            if (columns.isEmpty()) {
                columns = insertCols;
                log.info("Detected {} columns from SQL: {}", columns.size(), columns);
            }

            // Parse each VALUES row: (v1, v2, v3), (v4, v5, v6)
            List<List<String>> valueRows = parseValueRows(valuesPart);

            for (List<String> values : valueRows) {
                Map<String, String> data = new LinkedHashMap<>();

                for (int i = 0; i < insertCols.size(); i++) {
                    String colName = insertCols.get(i);
                    String value = i < values.size()
                            ? unquoteValue(values.get(i))
                            : null;
                    data.put(colName, value);
                }

                rows.add(ExcelRowDto.builder()
                        .rowNumber(rowIndex++)
                        .sourceFile(filePath.getFileName().toString())
                        .batchId(batchId)
                        .data(data)
                        .valid(true)
                        .rawRowData(String.join("|", data.values()
                                .stream()
                                .map(v -> v != null ? v : "")
                                .collect(Collectors.toList())))
                        .build());
            }
        }

        if (rows.isEmpty()) {
            log.warn("No INSERT rows parsed from SQL file: {}", filePath);
        } else {
            log.info("Parsed {} rows from SQL file", rows.size());
        }

        return rows;
    }

    /**
     * Parse the VALUES part of an INSERT into individual row lists.
     * Handles: (v1, v2), (v3, v4)
     * Handles: strings with commas inside quotes: ('hello, world', 2)
     */
    private List<List<String>> parseValueRows(String valuesPart) {
        List<List<String>> rows = new ArrayList<>();

        // Match each (...) group — handle nested quotes
        Pattern rowPattern = Pattern.compile(
                "\\(([^()]*(?:'[^']*'[^()]*)*)\\)"
        );
        Matcher m = rowPattern.matcher(valuesPart);

        while (m.find()) {
            String rowContent = m.group(1);
            List<String> values = splitValues(rowContent);
            if (!values.isEmpty()) {
                rows.add(values);
            }
        }

        return rows;
    }

    /**
     * Split a VALUES row content by comma,
     * respecting single-quoted strings.
     */
    private List<String> splitValues(String content) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (!inQuote && (c == '\'' || c == '"')) {
                inQuote = true;
                quoteChar = c;
                current.append(c);
            } else if (inQuote && c == quoteChar) {
                // Handle escaped quotes: '' inside single quotes
                if (i + 1 < content.length()
                        && content.charAt(i + 1) == quoteChar) {
                    current.append(c);
                    current.append(c);
                    i++; // skip next quote
                } else {
                    inQuote = false;
                    current.append(c);
                }
            } else if (!inQuote && c == ',') {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            values.add(current.toString().trim());
        }

        return values;
    }

    /**
     * Remove surrounding quotes and unescape content.
     * 'hello world' → hello world
     * NULL          → null (Java null)
     * 123           → "123"
     */
    private String unquoteValue(String raw) {
        if (raw == null) return null;
        raw = raw.trim();

        if (raw.equalsIgnoreCase("NULL")) return null;

        if ((raw.startsWith("'") && raw.endsWith("'")) ||
                (raw.startsWith("\"") && raw.endsWith("\""))) {
            String inner = raw.substring(1, raw.length() - 1);
            // Unescape doubled quotes
            inner = inner.replace("''", "'").replace("\"\"", "\"");
            return inner;
        }

        return raw;
    }
}
