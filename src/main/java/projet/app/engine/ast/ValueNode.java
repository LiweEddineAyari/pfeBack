package projet.app.engine.ast;

import projet.app.engine.enums.FormulaNodeType;
import projet.app.engine.visitor.FormulaAstVisitor;

public record ValueNode(Object value) implements FormulaNode {

    @Override
    public FormulaNodeType type() {
        return FormulaNodeType.VALUE;
    }

    @Override
    public <T> T accept(FormulaAstVisitor<T> visitor) {
        return visitor.visitValue(this);
    }
}
