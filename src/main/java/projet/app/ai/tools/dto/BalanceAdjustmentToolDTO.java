package projet.app.ai.tools.dto;

import lombok.Data;
import projet.app.dto.stresstest.BalanceOperationType;

import java.math.BigDecimal;

/**
 * Tool-boundary mirror of {@code BalanceAdjustmentDTO}: the {@code filters}
 * field is typed as {@link Object} (instead of Jackson {@code JsonNode}) so
 * Gson can deserialise the LLM's tool arguments without a custom TypeAdapter.
 */
@Data
public class BalanceAdjustmentToolDTO {

    private BalanceOperationType operation;

    private String field;

    private BigDecimal value;

    private Object filters;
}
