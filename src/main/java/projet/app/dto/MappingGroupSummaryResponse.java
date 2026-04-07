package projet.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MappingGroupSummaryResponse {

    private Integer configGroupNumber;
    private Integer mappingCount;
}
