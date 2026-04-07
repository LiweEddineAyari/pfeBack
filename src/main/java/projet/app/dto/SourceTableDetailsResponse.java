package projet.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceTableDetailsResponse {

    private String schema;
    private String tableName;
    private String qualifiedTable;
    private List<ColumnMeta> columns;
}
