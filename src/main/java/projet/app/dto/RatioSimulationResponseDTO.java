package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class RatioSimulationResponseDTO {

    private String mode;
    private Double value;
    private List<RatioDimensionValueDTO> rows;
    private String sqlExpression;
    private Set<String> referencedParameters;
}
