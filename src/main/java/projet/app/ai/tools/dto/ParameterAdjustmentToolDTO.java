package projet.app.ai.tools.dto;

import lombok.Data;
import projet.app.dto.stresstest.ParameterOperationType;

/**
 * Tool-boundary mirror of {@code ParameterAdjustmentDTO}: the {@code formula}
 * field is typed as {@link Object} (instead of Jackson {@code JsonNode}) so
 * Gson can deserialise the LLM's tool arguments without a custom TypeAdapter.
 */
@Data
public class ParameterAdjustmentToolDTO {

    private ParameterOperationType operation;

    private String code;

    private Double value;

    private Object formula;
}
