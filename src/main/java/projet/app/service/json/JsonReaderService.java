package projet.app.service.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import projet.app.dto.ExcelRowDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Service for reading JSON files and converting to ExcelRowDto format.
 * Supports both array of objects and single object JSON formats.
 */
@Slf4j
@Service
public class JsonReaderService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Read all records from a JSON file.
     * Supports:
     * - Array of objects: [{"col1": "val1", ...}, {"col1": "val2", ...}]
     * - Single object: {"col1": "val1", ...}
     */
    public List<ExcelRowDto> readJsonFile(Path filePath, String batchId) {
        log.info("Reading JSON file: {}", filePath);
        List<ExcelRowDto> rows = new ArrayList<>();

        try {
            String content = Files.readString(filePath);
            content = content.trim();

            List<Map<String, Object>> records;

            if (content.startsWith("[")) {
                // Array of objects
                records = objectMapper.readValue(content, new TypeReference<List<Map<String, Object>>>() {});
            } else if (content.startsWith("{")) {
                // Object can be either:
                // 1) A single record: {"col1":"v1", ...}
                // 2) A wrapper with records under a key: {"table_name":[{"col1":"v1"}, ...]}
                // 3) A wrapper with a single object record: {"table_name":{"col1":"v1"}}
                Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
                records = extractRecordsFromRoot(root);
            } else {
                throw new RuntimeException("Invalid JSON format. Expected array or object.");
            }

            log.info("Found {} records in JSON file", records.size());

            for (int i = 0; i < records.size(); i++) {
                Map<String, Object> record = records.get(i);
                ExcelRowDto dto = convertToDto(record, i + 1, filePath.getFileName().toString(), batchId);
                rows.add(dto);

                if ((i + 1) % 1000 == 0) {
                    log.info("Processed {} records...", i + 1);
                }
            }

            log.info("Read {} records from JSON file", rows.size());
            return rows;

        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON file: " + filePath, e);
        }
    }

    /**
     * Extract records from a root JSON object.
     * Prefers any root key that contains a list of objects, then a single object,
     * and falls back to treating root itself as one record.
     */
    private List<Map<String, Object>> extractRecordsFromRoot(Map<String, Object> root) {
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof List<?> listValue) {
                List<Map<String, Object>> extracted = extractObjectList(listValue);
                if (!extracted.isEmpty()) {
                    log.info("Detected wrapper key '{}' with {} records", entry.getKey(), extracted.size());
                    return extracted;
                }
            }
        }

        for (Map.Entry<String, Object> entry : root.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                @SuppressWarnings("unchecked")
                Map<String, Object> record = (Map<String, Object>) mapValue;
                log.info("Detected wrapper key '{}' with one object record", entry.getKey());
                return Collections.singletonList(record);
            }
        }

        return Collections.singletonList(root);
    }

    private List<Map<String, Object>> extractObjectList(List<?> listValue) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object item : listValue) {
            if (item instanceof Map<?, ?> mapItem) {
                @SuppressWarnings("unchecked")
                Map<String, Object> record = (Map<String, Object>) mapItem;
                records.add(record);
            }
        }
        return records;
    }

    /**
     * Convert a JSON object (Map) to ExcelRowDto.
     */
    private ExcelRowDto convertToDto(Map<String, Object> record, int rowNumber, String sourceFile, String batchId) {
        Map<String, String> data = new LinkedHashMap<>();
        StringBuilder rawData = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String key = entry.getKey().trim();
            String value = convertValueToString(entry.getValue());
            data.put(key, value);

            if (!first) {
                rawData.append("|");
            }
            rawData.append(value != null ? value : "");
            first = false;
        }

        return ExcelRowDto.builder()
                .rowNumber(rowNumber)
                .sourceFile(sourceFile)
                .batchId(batchId)
                .data(data)
                .valid(true)
                .rawRowData(rawData.toString())
                .build();
    }

    /**
     * Convert any JSON value to String representation.
     */
    private String convertValueToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number) {
            // Handle numeric values - convert whole numbers to integer format
            Number num = (Number) value;
            double doubleValue = num.doubleValue();
            if (doubleValue == Math.floor(doubleValue) && !Double.isInfinite(doubleValue)) {
                // It's a whole number, format as integer (no decimal)
                return String.valueOf(num.longValue());
            }
            return num.toString();
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        // For nested objects/arrays, convert to JSON string
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}
