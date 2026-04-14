package projet.app.engine.compiler;

import org.springframework.stereotype.Component;
import projet.app.engine.ast.FormulaDefinition;
import projet.app.engine.registry.FieldRegistry;
import projet.app.engine.registry.JoinResolution;

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
        SqlCompilationContext context = new SqlCompilationContext();

        SqlExpression expression = expressionCompiler.compile(definition.expression(), context);
        FilterBuildResult where = filterBuilder.build(definition.whereFilter(), context);
        JoinResolution joins = joinResolver.resolve(definition);

        List<String> groupBySqlExpressions = new ArrayList<>();
        for (String groupByField : definition.groupByFields()) {
            var field = fieldRegistry.resolve(groupByField);
            context.addReferencedField(field.fieldName());
            groupBySqlExpressions.add(fieldRegistry.toSqlExpression(field));
        }

        String sql = sqlQueryBuilder.build(
                expression.sql(),
                joins.joinClauses(),
                where.sql(),
                groupBySqlExpressions
        );

        return new CompiledSql(
                sql,
                context.getParameters(),
                context.getReferencedFields(),
                joins.joinClauses(),
                definition.groupByFields()
        );
    }
}
