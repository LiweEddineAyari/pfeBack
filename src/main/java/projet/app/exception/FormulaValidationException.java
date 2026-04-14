package projet.app.exception;

import java.util.List;

public class FormulaValidationException extends RuntimeException {

    private final List<String> errors;

    public FormulaValidationException(List<String> errors) {
        super("Invalid formula configuration");
        this.errors = errors == null ? List.of("Invalid formula configuration") : List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
