package projet.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FormulaRequestDTO {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "label is required")
    private String label;

    @NotNull(message = "formula is required")
    private JsonNode formula;

    private Boolean isActive;
}
