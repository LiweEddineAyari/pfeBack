package projet.app.dto.stresstest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class StressTestResponseDTO {

    private StressTestMethod method;
    private LocalDate referenceDate;

    private Integer factRowsLoaded;
    private Integer factRowsImpacted;

    private Set<String> affectedFields;
    private Set<String> affectedParameters;
    private Set<String> affectedRatios;

    private List<ParameterImpactDTO> parameters;
    private List<RatioImpactDTO> ratios;
}
