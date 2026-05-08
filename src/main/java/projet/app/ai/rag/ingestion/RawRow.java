package projet.app.ai.rag.ingestion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw representation of one Excel/CSV row. Column names → cell text. Holds enough
 * metadata for the chunking engine to decide what document_type to emit.
 */
public record RawRow(int rowIndex, String sheetName, Map<String, String> cells) {

    public static RawRow empty(int idx, String sheet) {
        return new RawRow(idx, sheet, new LinkedHashMap<>());
    }

    public String get(String column) {
        if (cells == null) {
            return null;
        }
        for (Map.Entry<String, String> e : cells.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(column)) {
                return e.getValue();
            }
        }
        return null;
    }
}
