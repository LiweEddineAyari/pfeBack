package projet.app.service.transform.tiers;

import java.util.Map;

public class AgentEcoTransformer {

    private static final Map<Integer, Integer> AGENTECO_MAPPING = Map.ofEntries(
        Map.entry(100, 21100),
        Map.entry(110, 23100),
        Map.entry(115, 21210),
        Map.entry(118, 21220),
        Map.entry(120, 21230),
        Map.entry(136, 26100),
        Map.entry(160, 21240),
        Map.entry(164, 21321),
        Map.entry(170, 21250),
        Map.entry(171, 21322),
        Map.entry(175, 21330),
        Map.entry(185, 26200),
        Map.entry(201, 23100),
        Map.entry(202, 23200),
        Map.entry(203, 23300),
        Map.entry(205, 27000),
        Map.entry(206, 27000),
        Map.entry(211, 25000),
        Map.entry(212, 25000),
        Map.entry(213, 25000),
        Map.entry(221, 22110),
        Map.entry(222, 22120),
        Map.entry(230, 22220),
        Map.entry(231, 22220),
        Map.entry(232, 22220),
        Map.entry(233, 22220),
        Map.entry(234, 22210),
        Map.entry(266, 21311),
        Map.entry(267, 21312),
        Map.entry(270, 21323),
        Map.entry(300, 24100),
        Map.entry(303, 24100),
        Map.entry(320, 24200),
        Map.entry(324, 24200),
        Map.entry(400, 24200)
    );

    public static Integer transform(String sourceValue) {
        if (sourceValue == null || sourceValue.isBlank()) {
            return null;
        }
        try {
            int key = Integer.parseInt(sourceValue.trim());
            return AGENTECO_MAPPING.get(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
