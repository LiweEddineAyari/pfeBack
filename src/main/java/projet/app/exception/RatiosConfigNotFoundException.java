package projet.app.exception;

public class RatiosConfigNotFoundException extends RuntimeException {

    public RatiosConfigNotFoundException(String code) {
        super("Ratios config not found for code: " + code);
    }
}
