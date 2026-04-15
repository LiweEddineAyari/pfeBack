package projet.app.engine.registry;

import java.util.List;

public record JoinDefinition(
        String key,
        String alias,
                String clause,
                List<String> dependsOn
) {

        public JoinDefinition {
                dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
}
