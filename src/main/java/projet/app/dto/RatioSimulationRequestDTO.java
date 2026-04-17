package projet.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RatioSimulationRequestDTO {

    @NotNull(message = "formula is required")
    private JsonNode formula;
}
