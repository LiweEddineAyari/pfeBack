package projet.app.engine.ast;

import projet.app.engine.enums.ArithmeticOperator;
import projet.app.engine.enums.FormulaNodeType;
import projet.app.engine.visitor.FormulaAstVisitor;

public record BinaryOperationNode(
        ArithmeticOperator operator,
        FormulaNode left,
        FormulaNode right
) implements FormulaNode {

    public BinaryOperationNode {
        if (operator == null) {
            throw new IllegalArgumentException("Binary operator is required");
        }
        if (left == null || right == null) {
            throw new IllegalArgumentException("Binary node requires left and right expressions");
        }
    }

    @Override
    public FormulaNodeType type() {
        return FormulaNodeType.valueOf(operator.name());
    }

    @Override
    public <T> T accept(FormulaAstVisitor<T> visitor) {
        return visitor.visitBinary(this);
    }
}
