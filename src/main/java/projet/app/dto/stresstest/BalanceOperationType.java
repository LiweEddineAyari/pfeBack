package projet.app.dto.stresstest;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum BalanceOperationType {
    SET,
    ADD,
    SUBTRACT;

    @JsonCreator
    public static BalanceOperationType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("balance operation is required (SET, ADD, SUBTRACT)");
        }
        try {
            return BalanceOperationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported balance operation: " + raw);
        }
    }
}
