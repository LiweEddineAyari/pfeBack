package projet.app.engine.enums;

import projet.app.exception.FormulaValidationException;

import java.util.List;
import java.util.Locale;

public enum FormulaNodeType {
    FIELD,
    VALUE,
    AGGREGATION,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE;

    public static FormulaNodeType from(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new FormulaValidationException(List.of("Node type is required"));
        }

        try {
            return FormulaNodeType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FormulaValidationException(List.of("Unsupported node type: " + rawType));
        }
    }
}