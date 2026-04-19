package projet.app.service.ratio;

import java.util.Map;

public record RatioFormulaExecutionResult(
        double value,
        Map<String, Double> resolvedParameters
) {
}
