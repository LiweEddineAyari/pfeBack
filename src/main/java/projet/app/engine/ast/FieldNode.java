package projet.app.engine.ast;

import projet.app.engine.enums.FormulaNodeType;
import projet.app.engine.visitor.FormulaAstVisitor;

public record FieldNode(String field) implements FormulaNode {

    public FieldNode {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Field node requires a non-empty field");
        }
    }

    @Override
    public FormulaNodeType type() {
        return FormulaNodeType.FIELD;
    }

    @Override
    public <T> T accept(FormulaAstVisitor<T> visitor) {
        return visitor.visitField(this);
    }
}
