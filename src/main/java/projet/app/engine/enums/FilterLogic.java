package projet.app.engine.enums;

import projet.app.exception.FormulaValidationException;

import java.util.List;
import java.util.Locale;

public enum FilterLogic {
    AND,
    OR;

    public static FilterLogic from(String rawLogic) {
        if (rawLogic == null || rawLogic.isBlank()) {
            return AND;
        }

        try {
            return FilterLogic.valueOf(rawLogic.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FormulaValidationException(List.of("Unsupported filter logic: " + rawLogic));
        }
    }
}