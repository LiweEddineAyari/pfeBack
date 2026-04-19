package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkDeleteResponseDTO {

    private int requestedCount;
    private int deletedCount;
    private List<String> deletedCodes;
    private List<String> missingCodes;
}
