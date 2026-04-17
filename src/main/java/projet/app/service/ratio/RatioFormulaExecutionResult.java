package projet.app.service.ratio;

import java.util.Map;

public record RatioFormulaExecutionResult(
        ParameterResult result,
        Map<String, ParameterResult> resolvedParameters
) {
}
