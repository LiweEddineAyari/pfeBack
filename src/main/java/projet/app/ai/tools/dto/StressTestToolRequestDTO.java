package projet.app.ai.tools.dto;

import lombok.Data;
import projet.app.dto.stresstest.StressTestMethod;

import java.time.LocalDate;
import java.util.List;

/**
 * Tool-boundary mirror of {@code StressTestRequestDTO}. LangChain4j deserialises
 * LLM tool arguments via Gson, which cannot instantiate Jackson's abstract
 * {@code JsonNode}. This DTO swaps the JsonNode-typed fields in the nested
 * adjustment objects for plain {@code Object} (Gson maps that to LinkedTreeMap),
 * and is converted to the real DTO before the loopback REST call.
 */
@Data
public class StressTestToolRequestDTO {

    private StressTestMethod method;

    private LocalDate referenceDate;

    private List<BalanceAdjustmentToolDTO> balanceAdjustments;

    private List<ParameterAdjustmentToolDTO> parameterAdjustments;

    private List<String> parameterCodes;

    private List<String> ratioCodes;
}
