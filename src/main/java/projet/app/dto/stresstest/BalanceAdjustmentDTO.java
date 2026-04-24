package projet.app.dto.stresstest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BalanceAdjustmentDTO {

    @NotNull(message = "operation is required")
    private BalanceOperationType operation;

    @NotNull(message = "field is required")
    private String field;

    @NotNull(message = "value is required")
    private BigDecimal value;

    private JsonNode filters;
}
