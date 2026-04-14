package projet.app.exception;

public class ParameterConfigNotFoundException extends RuntimeException {

    public ParameterConfigNotFoundException(String code) {
        super("Parameter config not found for code: " + code);
    }
}
