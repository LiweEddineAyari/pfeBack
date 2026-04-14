package projet.app.engine.compiler;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SqlQueryBuilder {

    private static final String BASE_FROM = " FROM datamart.fact_balance f";

    public String build(
            String expressionSql,
            List<String> joins,
            String globalWhereSql,
            List<String> groupByExpressions
    ) {
        StringBuilder builder = new StringBuilder();

        if (groupByExpressions == null || groupByExpressions.isEmpty()) {
            builder.append("SELECT ").append(expressionSql).append(" AS value");
        } else {
            String selectGroups = groupByExpressions.stream()
                    .map(SqlQueryBuilder::aliasGroupExpression)
                    .collect(Collectors.joining(", "));
            builder.append("SELECT ")
                    .append(selectGroups)
                    .append(", ")
                    .append(expressionSql)
                    .append(" AS value");
        }

        builder.append(BASE_FROM);

        if (joins != null && !joins.isEmpty()) {
            builder.append(" ").append(String.join(" ", joins));
        }

        if (globalWhereSql != null && !globalWhereSql.isBlank()) {
            builder.append(" WHERE ").append(globalWhereSql);
        }

        if (groupByExpressions != null && !groupByExpressions.isEmpty()) {
            builder.append(" GROUP BY ").append(String.join(", ", groupByExpressions));
        }

        return builder.toString();
    }

    private static String aliasGroupExpression(String expr) {
        String alias = expr
                .replace("CAST(", "")
                .replace(" AS TEXT)", "")
                .replace('.', '_')
                .replaceAll("[^A-Za-z0-9_]", "");
        if (alias.isBlank()) {
            alias = "group_col";
        }
        return expr + " AS " + alias;
    }
}
