package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class RatioExecutionResponseDTO {

    private String code;
    private LocalDate referenceDate;
    private Double value;
    private String sqlExpression;
    private Set<String> referencedParameters;
    private Map<String, Double> resolvedParameters;
}
