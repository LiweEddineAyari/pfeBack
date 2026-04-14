package projet.app.engine.compiler;

import java.util.List;
import java.util.Set;

public record CompiledSql(
        String sql,
        List<Object> parameters,
        Set<String> referencedFields,
        List<String> joins,
        List<String> groupByFields
) {
}
