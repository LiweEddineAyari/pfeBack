package projet.app.dto.stresstest;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum ParameterOperationType {
    MULTIPLY,
    ADD,
    REPLACE,
    MODIFY_FORMULA;

    @JsonCreator
    public static ParameterOperationType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "parameter operation is required (MULTIPLY, ADD, REPLACE, MODIFY_FORMULA)"
            );
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return ParameterOperationType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported parameter operation: " + raw);
        }
    }
}
