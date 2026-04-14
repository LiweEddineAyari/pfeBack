package projet.app.engine.compiler;

import java.util.List;

public record FilterBuildResult(
        String sql,
        List<Object> parameters
) {
}
