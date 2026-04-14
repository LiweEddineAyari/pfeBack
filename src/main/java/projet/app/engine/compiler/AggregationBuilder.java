package projet.app.engine.compiler;

import org.springframework.stereotype.Service;
import projet.app.engine.ast.AggregationNode;
import projet.app.engine.ast.FormulaNode;
import projet.app.engine.enums.AggregationFunction;
import projet.app.engine.enums.FormulaValueType;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;
import projet.app.exception.FormulaValidationException;

import java.util.List;
import java.util.function.Function;

@Service
public class AggregationBuilder {

    private final FieldRegistry fieldRegistry;
    private final FilterBuilder filterBuilder;

    public AggregationBuilder(FieldRegistry fieldRegistry, FilterBuilder filterBuilder) {
        this.fieldRegistry = fieldRegistry;
        this.filterBuilder = filterBuilder;
    }

    public SqlExpression build(
            AggregationNode node,
            SqlCompilationContext context,
            Function<FormulaNode, SqlExpression> expressionCompiler
    ) {
        String operandSql;
        FormulaValueType operandType;

        if (node.expression() != null) {
            SqlExpression inner = expressionCompiler.apply(node.expression());
            operandSql = inner.sql();
            operandType = inner.valueType();
        } else if (node.field() != null && !node.field().isBlank()) {
            FieldDefinition field = fieldRegistry.resolve(node.field());
            context.addReferencedField(field.fieldName());
            operandSql = fieldRegistry.toSqlExpression(field);
            operandType = fieldRegistry.getFormulaValueType(field.fieldName());
        } else if (node.function() == AggregationFunction.COUNT) {
            operandSql = "*";
            operandType = FormulaValueType.NUMERIC;
        } else {
            throw new FormulaValidationException(List.of("Aggregation requires field or expression"));
        }

        if (node.function() == AggregationFunction.COUNT && "*".equals(operandSql) && node.distinct()) {
            throw new FormulaValidationException(List.of("COUNT DISTINCT requires a field or expression"));
        }

        if (node.function().requiresNumericField() && !operandType.isNumeric()) {
            throw new FormulaValidationException(List.of(
                    "Aggregation " + node.function() + " requires numeric input"
            ));
        }

        if (node.filters() != null && !node.filters().isEmpty()) {
            FilterBuildResult filter = filterBuilder.build(node.filters(), context);
            if ("*".equals(operandSql) && node.function() == AggregationFunction.COUNT) {
                operandSql = "1";
            }
            operandSql = "CASE WHEN (" + filter.sql() + ") THEN " + operandSql + " END";
        }

        String distinctPrefix = node.distinct() ? "DISTINCT " : "";
        String sql = node.function().name() + "(" + distinctPrefix + operandSql + ")";
        return new SqlExpression(sql, FormulaValueType.NUMERIC, true);
    }
}
