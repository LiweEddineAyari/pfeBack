package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class RatioExecutionResponseDTO {

    private String code;
    private LocalDate referenceDate;
    private String mode;
    private Double value;
    private List<RatioDimensionValueDTO> rows;
    private String sqlExpression;
    private Set<String> referencedParameters;
    private Map<String, Double> resolvedParameters;
    private Map<String, List<RatioDimensionValueDTO>> resolvedParameterRows;
}
