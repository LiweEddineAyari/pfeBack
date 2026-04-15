package projet.app.engine.compiler;

import projet.app.engine.ast.OrderByNode;

import java.util.List;
import java.util.Set;

public record CompiledSql(
        String sql,
        List<Object> parameters,
        Set<String> referencedFields,
        List<String> joins,
        List<String> groupByFields,
        List<OrderByNode> orderBy,
        Integer limit,
        Integer top
) {
}
