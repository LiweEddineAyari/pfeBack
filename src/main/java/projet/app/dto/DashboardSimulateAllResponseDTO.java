package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardSimulateAllResponseDTO {

    private int datesFound;
    private List<LocalDate> dates;
    private int ratiosProcessed;
    private int inserted;
    private int skipped;
    private List<Map<String, Object>> errors;
}
