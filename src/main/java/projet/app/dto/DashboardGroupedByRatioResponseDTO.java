package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Dashboard values grouped by ratio code (e.g. R1), each mapping reference dates (ISO) to stored values.
 */
@Data
@Builder
public class DashboardGroupedByRatioResponseDTO {

    /**
     * Ratio code → (reference date as yyyy-MM-dd → ratio value).
     */
    private Map<String, Map<String, Double>> ratios;
}
