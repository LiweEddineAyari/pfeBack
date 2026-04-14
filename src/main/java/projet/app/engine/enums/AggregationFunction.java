package projet.app.engine.enums;

import projet.app.exception.FormulaValidationException;

import java.util.List;
import java.util.Locale;

public enum AggregationFunction {
    SUM,
    AVG,
    COUNT,
    MIN,
    MAX;

    public static AggregationFunction from(String rawFunction) {
        if (rawFunction == null || rawFunction.isBlank()) {
            throw new FormulaValidationException(List.of("Aggregation function is required"));
        }

        try {
            return AggregationFunction.valueOf(rawFunction.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FormulaValidationException(List.of("Unsupported aggregation function: " + rawFunction));
        }
    }

    public boolean requiresNumericField() {
        return this == SUM || this == AVG;
    }
}