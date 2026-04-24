package projet.app.exception;

import java.util.List;

/**
 * Raised by the stress-test engine for business rule violations (unbalanced simulation,
 * unknown parameter, unsupported operation, ...).
 *
 * <p>The {@link #errorCode} follows the catalog documented in
 * {@code docs/stress-test-api-reference.md} (e.g. {@code UNBALANCED_SIMULATION}).</p>
 */
public class StressTestException extends RuntimeException {

    private final String errorCode;
    private final List<String> details;

    public StressTestException(String errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public StressTestException(String errorCode, String message, List<String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<String> getDetails() {
        return details;
    }
}
