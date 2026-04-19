package projet.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class RatioSimulationResponseDTO {

    private Double value;
    private String sqlExpression;
    private Set<String> referencedParameters;
}
