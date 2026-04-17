package projet.app.engine.compiler;

import org.springframework.stereotype.Component;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.ast.OrderByNode;
import projet.app.engine.registry.FieldRegistry;
import projet.app.engine.registry.JoinResolution;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class FormulaSqlCompiler {

    private final ExpressionCompiler expressionCompiler;
    private final JoinResolver joinResolver;
    private final FilterBuilder filterBuilder;
    private final SqlQueryBuilder sqlQueryBuilder;
    private final FieldRegistry fieldRegistry;

    public FormulaSqlCompiler(
            ExpressionCompiler expressionCompiler,
            JoinResolver joinResolver,
            FilterBuilder filterBuilder,
            SqlQueryBuilder sqlQueryBuilder,
            FieldRegistry fieldRegistry
    ) {
        this.expressionCompiler = expressionCompiler;
        this.joinResolver = joinResolver;
        this.filterBuilder = filterBuilder;
        this.sqlQueryBuilder = sqlQueryBuilder;
        this.fieldRegistry = fieldRegistry;
    }

    public CompiledSql compile(FormulaDefinition definition) {
        return compile(definition, null);
    }

    public CompiledSql compile(FormulaDefinition definition, LocalDate referenceDate) {
        SqlCompilationContext context = new SqlCompilationContext();

        SqlExpression expression = expressionCompiler.compile(definition.expression(), context);
        FilterBuildResult where = filterBuilder.build(definition.whereFilter(), context);
        JoinResolution joins = joinResolver.resolve(definition);
        List<String> joinClauses = new ArrayList<>(joins.joinClauses());

        List<String> groupBySqlExpressions = new ArrayList<>();
        for (String groupByField : definition.groupByFields()) {
            var field = fieldRegistry.resolve(groupByField);
            context.addReferencedField(field.fieldName());
            groupBySqlExpressions.add(fieldRegistry.toSqlExpression(field));
        }

        List<String> orderBySqlExpressions = new ArrayList<>();
        for (OrderByNode orderByNode : definition.orderBy()) {
            if ("value".equalsIgnoreCase(orderByNode.field())) {
                orderBySqlExpressions.add("value " + orderByNode.direction().name());
                continue;
            }

            var field = fieldRegistry.resolve(orderByNode.field());
            context.addReferencedField(field.fieldName());
            orderBySqlExpressions.add(fieldRegistry.toSqlExpression(field) + " " + orderByNode.direction().name());
        }

        Integer rowLimit = definition.limit() != null ? definition.limit() : definition.top();

        String whereSql = where.sql();
        if (referenceDate != null) {
            if (!joinClauses.contains(JoinKey.SUB_DIM_DATE_FACT.getJoinSql())) {
                joinClauses.add(JoinKey.SUB_DIM_DATE_FACT.getJoinSql());
            }

            whereSql = (whereSql == null || whereSql.isBlank())
                    ? "sdatef.date_value = ?"
                    : "(" + whereSql + ") AND sdatef.date_value = ?";

            context.addParameter(referenceDate);
        }

        String sql = sqlQueryBuilder.build(
                expression.sql(),
            joinClauses,
            whereSql,
                groupBySqlExpressions,
                orderBySqlExpressions,
                rowLimit
        );

        return new CompiledSql(
                sql,
                context.getParameters(),
                context.getReferencedFields(),
                joinClauses,
                definition.groupByFields(),
                definition.orderBy(),
                definition.limit(),
                definition.top()
        );
    }
}
