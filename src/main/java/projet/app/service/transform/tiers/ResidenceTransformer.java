package projet.app.service.transform.tiers;

import java.util.HashMap;
import java.util.Map;

public class ResidenceTransformer {

    private static final Map<String, Integer> RESIDENCE_MAPPING = new HashMap<>();

    static {
        // Senegal
        RESIDENCE_MAPPING.put("SN", 110);

        // UEMOA countries
        RESIDENCE_MAPPING.put("TG", 120);
        RESIDENCE_MAPPING.put("NE", 120);
        RESIDENCE_MAPPING.put("GW", 120);
        RESIDENCE_MAPPING.put("CI", 120);
        RESIDENCE_MAPPING.put("BJ", 120);
        RESIDENCE_MAPPING.put("ML", 120);
        RESIDENCE_MAPPING.put("BF", 120);

        // CEMAC
        RESIDENCE_MAPPING.put("TD", 141);
        RESIDENCE_MAPPING.put("CM", 141);
        RESIDENCE_MAPPING.put("GQ", 141);
        RESIDENCE_MAPPING.put("CG", 141);
        RESIDENCE_MAPPING.put("KM", 141);
        RESIDENCE_MAPPING.put("CF", 141);
        RESIDENCE_MAPPING.put("GA", 141);

        // West Africa
        RESIDENCE_MAPPING.put("GM", 142);
        RESIDENCE_MAPPING.put("SL", 142);
        RESIDENCE_MAPPING.put("LR", 142);
        RESIDENCE_MAPPING.put("GH", 142);
        RESIDENCE_MAPPING.put("NG", 142);
        RESIDENCE_MAPPING.put("GN", 142);
        RESIDENCE_MAPPING.put("CV", 142);

        // EU countries
        RESIDENCE_MAPPING.put("FR", 143);
        RESIDENCE_MAPPING.put("DE", 143);
        RESIDENCE_MAPPING.put("IT", 143);
        RESIDENCE_MAPPING.put("ES", 143);
        RESIDENCE_MAPPING.put("PT", 143);
        RESIDENCE_MAPPING.put("BE", 143);
        RESIDENCE_MAPPING.put("NL", 143);
        RESIDENCE_MAPPING.put("IE", 143);
        RESIDENCE_MAPPING.put("GR", 143);
        RESIDENCE_MAPPING.put("FI", 143);
        RESIDENCE_MAPPING.put("AT", 143);
        RESIDENCE_MAPPING.put("MT", 143);
        RESIDENCE_MAPPING.put("CY", 143);

        // Most other countries
        RESIDENCE_MAPPING.put("US", 144);
        RESIDENCE_MAPPING.put("CA", 144);
        RESIDENCE_MAPPING.put("CN", 144);
        RESIDENCE_MAPPING.put("JP", 144);
        RESIDENCE_MAPPING.put("AU", 144);
        RESIDENCE_MAPPING.put("BR", 144);
        RESIDENCE_MAPPING.put("RU", 144);
        RESIDENCE_MAPPING.put("TR", 144);
        RESIDENCE_MAPPING.put("MA", 144);
        RESIDENCE_MAPPING.put("DZ", 144);
        RESIDENCE_MAPPING.put("TN", 144);
    }

    public static Integer transform(String residence) {
        if (residence == null) {
            return null;
        }
        return RESIDENCE_MAPPING.getOrDefault(residence.trim().toUpperCase(), 144);
    }
}
