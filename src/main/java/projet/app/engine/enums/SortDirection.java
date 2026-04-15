package projet.app.engine.enums;

import projet.app.exception.FormulaValidationException;

import java.util.List;
import java.util.Locale;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection from(String rawDirection) {
        if (rawDirection == null || rawDirection.isBlank()) {
            return ASC;
        }

        try {
            return SortDirection.valueOf(rawDirection.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FormulaValidationException(List.of("Unsupported order direction: " + rawDirection));
        }
    }
}
