package projet.app.engine.registry;

import java.util.List;

public record JoinResolution(
        List<String> joinClauses,
        List<String> tableAliases
) {
}
