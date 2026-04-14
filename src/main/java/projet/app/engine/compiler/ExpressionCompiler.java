package projet.app.engine.compiler;

import org.springframework.stereotype.Service;
import projet.app.engine.ast.AggregationNode;
import projet.app.engine.ast.BinaryOperationNode;
import projet.app.engine.ast.FieldNode;
import projet.app.engine.ast.FormulaNode;
import projet.app.engine.ast.ValueNode;
import projet.app.engine.enums.ArithmeticOperator;
import projet.app.engine.enums.FormulaValueType;
import projet.app.engine.registry.FieldDefinition;
import projet.app.engine.registry.FieldRegistry;
import projet.app.exception.FormulaValidationException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ExpressionCompiler {

    private final FieldRegistry fieldRegistry;
    private final AggregationBuilder aggregationBuilder;

    public ExpressionCompiler(FieldRegistry fieldRegistry, AggregationBuilder aggregationBuilder) {
        this.fieldRegistry = fieldRegistry;
        this.aggregationBuilder = aggregationBuilder;
    }

    public SqlExpression compile(FormulaNode node, SqlCompilationContext context) {
        if (node instanceof FieldNode fieldNode) {
            FieldDefinition definition = fieldRegistry.resolve(fieldNode.field());
            context.addReferencedField(definition.fieldName());
            return new SqlExpression(
                    fieldRegistry.toSqlExpression(definition),
                    fieldRegistry.getFormulaValueType(definition.fieldName()),
                    false
            );
        }

        if (node instanceof ValueNode valueNode) {
            context.addParameter(valueNode.value());
            return new SqlExpression("?", inferValueType(valueNode.value()), false);
        }

        if (node instanceof AggregationNode aggregationNode) {
            return aggregationBuilder.build(aggregationNode, context, n -> compile(n, context));
        }

        if (node instanceof BinaryOperationNode binaryNode) {
            SqlExpression left = compile(binaryNode.left(), context);
            SqlExpression right = compile(binaryNode.right(), context);

            if (!left.valueType().isNumeric() || !right.valueType().isNumeric()) {
                throw new FormulaValidationException(List.of("Arithmetic operations require numeric operands"));
            }

            String sql;
            if (binaryNode.operator() == ArithmeticOperator.DIVIDE) {
                sql = "(" + left.sql() + " / NULLIF(" + right.sql() + ", 0))";
            } else {
                sql = "(" + left.sql() + " " + binaryNode.operator().getSqlSymbol() + " " + right.sql() + ")";
            }

            return new SqlExpression(sql, FormulaValueType.NUMERIC, left.aggregated() || right.aggregated());
        }

        throw new FormulaValidationException(List.of("Unsupported expression node: " + node.getClass().getSimpleName()));
    }

    private FormulaValueType inferValueType(Object value) {
        if (value == null) {
            return FormulaValueType.UNKNOWN;
        }
        if (value instanceof Number || value instanceof BigDecimal) {
            return FormulaValueType.NUMERIC;
        }
        if (value instanceof Boolean) {
            return FormulaValueType.BOOLEAN;
        }
        return FormulaValueType.STRING;
    }
}
