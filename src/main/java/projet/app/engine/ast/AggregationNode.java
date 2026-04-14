package projet.app.engine.ast;

import projet.app.engine.enums.AggregationFunction;
import projet.app.engine.enums.FormulaNodeType;
import projet.app.engine.visitor.FormulaAstVisitor;

public record AggregationNode(
        AggregationFunction function,
        String field,
    FormulaNode expression,
    FilterGroupNode filters,
    boolean distinct
) implements FormulaNode {

    public AggregationNode {
        if (function == null) {
            throw new IllegalArgumentException("Aggregation function is required");
        }

        boolean hasField = field != null && !field.isBlank();
        boolean hasExpression = expression != null;
        if (hasField && hasExpression) {
            throw new IllegalArgumentException("Aggregation cannot define both field and expression");
        }
        if (!hasField && !hasExpression && function != AggregationFunction.COUNT) {
            throw new IllegalArgumentException("Aggregation requires field or expression");
        }
    }

    @Override
    public FormulaNodeType type() {
        return FormulaNodeType.AGGREGATION;
    }

    @Override
    public <T> T accept(FormulaAstVisitor<T> visitor) {
        return visitor.visitAggregation(this);
    }
}
