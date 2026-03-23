package projet.app.service.transform.contrat;

public class TypeContratTransformer {

    public static String transform(String sourceValue) {
        if (sourceValue == null || sourceValue.isBlank()) {
            return null;
        }
        String trimmed = sourceValue.trim().toLowerCase();
        return switch (trimmed) {
            case "p" -> "PRET";
            case "d" -> "COMPTE ORDINAIRE";
            default -> sourceValue;
        };
    }
}
