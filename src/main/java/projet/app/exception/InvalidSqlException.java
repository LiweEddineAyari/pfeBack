package projet.app.exception;

import java.util.List;

public class InvalidSqlException extends RuntimeException {

    private final List<String> details;

    public InvalidSqlException(String message, List<String> details) {
        super(message == null || message.isBlank() ? "Unsupported SQL structure" : message);
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public List<String> getDetails() {
        return details;
    }
}
