package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class LoadFromDbResponse {
    private String status;
    private int rowCount;
    private String sourceTable;
    private String targetTable;
    private String mappingUsed;
    private Map<String, String> mappedColumns;
}
