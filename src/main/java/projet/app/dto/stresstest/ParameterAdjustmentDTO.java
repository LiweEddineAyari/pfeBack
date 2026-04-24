package projet.app.dto.stresstest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ParameterAdjustmentDTO {

    @NotNull(message = "operation is required")
    private ParameterOperationType operation;

    @NotBlank(message = "code is required")
    private String code;

    /**
     * Scalar used by MULTIPLY, ADD, REPLACE operations.
     * Ignored for MODIFY_FORMULA.
     */
    private Double value;

    /**
     * Replacement formula JSON used by MODIFY_FORMULA.
     * Must match the parameter formula grammar (same structure as {@code FormulaRequestDTO.formula}).
     */
    private JsonNode formula;
}
