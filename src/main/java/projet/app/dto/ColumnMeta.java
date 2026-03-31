package projet.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMeta {
    private String columnName;
    private String dataType;
    private boolean nullable;
}
