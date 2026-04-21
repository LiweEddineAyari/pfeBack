package projet.app.exception;

public class FamilleRatiosNotFoundException extends RuntimeException {

    public FamilleRatiosNotFoundException(Long id) {
        super("Famille ratios not found for id: " + id);
    }
}