package projet.app.dto.stresstest;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum StressTestMethod {
    BALANCE,
    PARAMETER;

    @JsonCreator
    public static StressTestMethod from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("method is required (BALANCE or PARAMETER)");
        }
        try {
            return StressTestMethod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported stress test method: " + raw);
        }
    }
}
