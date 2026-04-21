package projet.app.exception;

public class CategorieRatiosNotFoundException extends RuntimeException {

    public CategorieRatiosNotFoundException(Long id) {
        super("Categorie ratios not found for id: " + id);
    }
}