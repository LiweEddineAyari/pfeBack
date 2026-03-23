package projet.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Generic DTO representing a raw row from an Excel file.
 * Used to transfer data from reader to processor without type constraints.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExcelRowDto {
    
    private int rowNumber;
    private Map<String, String> data;
    private String rawRowData;
    private String sourceFile;
    private String batchId;
    private boolean valid;
    private String validationErrors;
    
    /**
     * Safely get a column value with null handling.
     */
    public String getValue(String columnName) {
        if (data == null || columnName == null) {
            return null;
        }
        return data.get(columnName.toUpperCase().trim());
    }
    
    /**
     * Check if a column exists in the row.
     */
    public boolean hasColumn(String columnName) {
        if (data == null || columnName == null) {
            return false;
        }
        return data.containsKey(columnName.toUpperCase().trim());
    }
}
