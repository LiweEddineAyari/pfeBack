package projet.app.service.transform.contrat;

import java.util.HashMap;
import java.util.Map;

public class ObjetFinanceTransformer {

    private static final Map<Integer, Integer> OBJET_MAPPING = new HashMap<>();

    static {
        OBJET_MAPPING.put(10234, 1340);

        OBJET_MAPPING.put(14256, 1350);
        OBJET_MAPPING.put(14257, 1350);
        OBJET_MAPPING.put(14258, 1350);
        OBJET_MAPPING.put(14259, 1320);
        OBJET_MAPPING.put(14260, 1350);

        OBJET_MAPPING.put(18296, 1340);
        OBJET_MAPPING.put(18297, 1340);

        OBJET_MAPPING.put(20134, 1340);
        OBJET_MAPPING.put(20135, 1340);
        OBJET_MAPPING.put(20136, 1340);
        OBJET_MAPPING.put(20137, 1340);
        OBJET_MAPPING.put(20138, 1340);
        OBJET_MAPPING.put(20139, 1340);
        OBJET_MAPPING.put(20143, 1340);
        OBJET_MAPPING.put(20147, 1340);
        OBJET_MAPPING.put(20156, 1340);
        OBJET_MAPPING.put(20157, 1340);
        OBJET_MAPPING.put(20158, 1340);
        OBJET_MAPPING.put(20163, 1340);
        OBJET_MAPPING.put(20167, 1340);
        OBJET_MAPPING.put(20173, 1340);
        OBJET_MAPPING.put(20174, 1340);
        OBJET_MAPPING.put(20175, 1340);
        OBJET_MAPPING.put(20176, 1340);
        OBJET_MAPPING.put(20186, 1340);
        OBJET_MAPPING.put(20195, 1340);
        OBJET_MAPPING.put(20196, 1340);
        OBJET_MAPPING.put(20197, 1340);

        OBJET_MAPPING.put(30411, 1311);
        OBJET_MAPPING.put(30412, 1311);
        OBJET_MAPPING.put(30413, 1311);
        OBJET_MAPPING.put(30414, 1311);
        OBJET_MAPPING.put(30415, 1311);
        OBJET_MAPPING.put(30421, 1311);
        OBJET_MAPPING.put(30422, 1311);
        OBJET_MAPPING.put(30425, 1311);
        OBJET_MAPPING.put(30431, 1311);
        OBJET_MAPPING.put(30432, 1311);
        OBJET_MAPPING.put(30435, 1311);
        OBJET_MAPPING.put(30441, 1311);
        OBJET_MAPPING.put(30442, 1311);
        OBJET_MAPPING.put(30443, 1311);
        OBJET_MAPPING.put(30449, 1311);
        OBJET_MAPPING.put(30455, 1311);

        OBJET_MAPPING.put(30461, 1312);
        OBJET_MAPPING.put(30462, 1312);
        OBJET_MAPPING.put(30463, 1312);
        OBJET_MAPPING.put(30464, 1312);
        OBJET_MAPPING.put(30465, 1312);
        OBJET_MAPPING.put(30466, 1312);
        OBJET_MAPPING.put(30469, 1312);

        OBJET_MAPPING.put(40315, 1350);
        OBJET_MAPPING.put(40316, 1350);
        OBJET_MAPPING.put(40317, 1350);
        OBJET_MAPPING.put(40318, 1350);

        OBJET_MAPPING.put(48350, 1330);
        OBJET_MAPPING.put(48351, 1330);
        OBJET_MAPPING.put(48352, 1330);
        OBJET_MAPPING.put(48361, 1330);
        OBJET_MAPPING.put(48369, 1330);
        OBJET_MAPPING.put(48370, 1330);
        OBJET_MAPPING.put(48371, 1330);
        OBJET_MAPPING.put(48372, 1330);
        OBJET_MAPPING.put(48396, 1330);
        OBJET_MAPPING.put(48397, 1330);

        OBJET_MAPPING.put(52013, 1340);

        OBJET_MAPPING.put(53048, 1312);
        OBJET_MAPPING.put(53049, 1311);

        OBJET_MAPPING.put(54097, 1330);
        OBJET_MAPPING.put(54098, 1350);

        OBJET_MAPPING.put(62503, 1340);
        OBJET_MAPPING.put(62504, 1340);

        OBJET_MAPPING.put(63517, 1311);
        OBJET_MAPPING.put(63518, 1312);
        OBJET_MAPPING.put(63519, 1311);
        OBJET_MAPPING.put(63520, 1312);

        OBJET_MAPPING.put(64597, 1330);
        OBJET_MAPPING.put(64598, 1330);
    }

    public static Integer transform(String sourceValue) {
        if (sourceValue == null || sourceValue.isEmpty()) {
            return null;
        }
        try {
            Integer source = Integer.parseInt(sourceValue.trim());
            return OBJET_MAPPING.get(source);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
